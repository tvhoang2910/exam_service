package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.dto.CreateExamRequest;
import com.exam_bank.exam_service.dto.ExamResponse;
import com.exam_bank.exam_service.dto.TagDto;
import com.exam_bank.exam_service.dto.internal.AiQuestionDto;
import com.exam_bank.exam_service.dto.internal.AiOptionDto;
import com.exam_bank.exam_service.dto.message.ExamSyncEvent;
import com.exam_bank.exam_service.entity.*;
import com.exam_bank.exam_service.feature.reporting.repository.QuestionReportHistoryRepository;
import com.exam_bank.exam_service.feature.reporting.repository.QuestionReportRepository;
import com.exam_bank.exam_service.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamManagementService {

    private static final int DEFAULT_TEASER_QUESTION_COUNT = 2;
    private static final int MIN_TEASER_QUESTION_COUNT = 1;
    private static final int MAX_TEASER_QUESTION_COUNT = 2;

    private final OnlineExamRepository examRepo;
    private final ExamAttemptRepository examAttemptRepo;
    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository optionRepo;
    private final ExamAttemptAnswerRepository examAttemptAnswerRepo;
    private final QuestionReviewEventRepository questionReviewEventRepo;
    private final QuestionReportRepository questionReportRepo;
    private final QuestionReportHistoryRepository questionReportHistoryRepo;
    private final TagRepository tagRepo;
    private final TagService tagService;
    private final ExamFlowCacheService examFlowCacheService;
    private final ExamAuditService examAuditService;
    private final AuthenticatedUserService authenticatedUserService;
    private final RabbitMQEventPublisher rabbitMQEventPublisher;

    @Transactional
    @CacheEvict(cacheNames = {"publicExams", "publicExamDetail", "managedExams",
            "managedExamDetail"}, allEntries = true)
    public ExamResponse createManualExam(CreateExamRequest request) {
        OnlineExam exam = buildExamEntity(new OnlineExam(), request);
        exam.setSource(OnlineExamSource.MANUAL_CREATED);
        exam.setStatus(OnlineExamStatus.DRAFT);
        OnlineExam savedExam = examRepo.save(exam);
        upsertQuestions(savedExam, request.getQuestions());
        examFlowCacheService.evictExam(savedExam.getId());
        publishSyncEvent(savedExam, "UPSERT");
        return mapExamToResponse(savedExam, false, true, true, null, false);
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> getManagedExams() {
        // Include all statuses: DRAFT, PUBLISHED, ARCHIVED
        List<OnlineExam> exams = examRepo.findAllByOrderByCreatedAtDesc();
        return mapExamListToSummary(exams);
    }

    @Transactional(readOnly = true)
    public ExamResponse getManagedExamById(Long examId) {
        OnlineExam exam = findExamOrThrow(examId);
        return mapExamToResponse(exam, true, true, true, null, false);
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicExams", "publicExamDetail", "managedExams",
            "managedExamDetail"}, allEntries = true)
    public ExamResponse updateExam(Long examId, CreateExamRequest request) {
        OnlineExam existing = findExamOrThrow(examId);

        long attemptCount = examAttemptRepo.countByExamId(examId);
        List<Question> existingQuestions = questionRepo.findByExamIdOrderByIdAsc(examId);
        boolean questionTreeChanged = isQuestionTreeChanged(existingQuestions, request.getQuestions());

        // Keep question tree immutable once historical attempts exist, but allow
        // metadata updates.
        if (attemptCount > 0 && questionTreeChanged) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Cannot update questions because attempts already exist. You can still update exam metadata (title, duration, passing score, max attempts, tags, status). Create a new exam version to change questions.");
        }

        OnlineExam updatedExam = examRepo.save(buildExamEntity(existing, request));

        // Avoid expensive delete/insert cycles when questions/options are unchanged.
        if (attemptCount == 0 && questionTreeChanged) {
            deleteQuestionsAndOptions(existingQuestions);
            upsertQuestions(updatedExam, request.getQuestions());
        }

        examFlowCacheService.evictExam(updatedExam.getId());
        publishSyncEvent(updatedExam, "UPSERT");
        return mapExamToResponse(updatedExam, false, true, true, null, false);
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicExams", "publicExamDetail", "managedExams",
            "managedExamDetail"}, allEntries = true)
    public void deleteExam(Long examId) {
        OnlineExam existing = findExamOrThrow(examId);
        List<Question> existingQuestions = questionRepo.findByExamIdOrderByIdAsc(examId);
        List<Long> questionIds = existingQuestions.stream().map(BaseEntity::getId).toList();
        List<Long> attemptIds = examAttemptRepo.findIdsByExamId(examId);

        Set<Long> reportIds = new LinkedHashSet<>();
        if (!attemptIds.isEmpty()) {
            reportIds.addAll(questionReportRepo.findIdsByAttemptIdIn(attemptIds));
        }
        if (!questionIds.isEmpty()) {
            reportIds.addAll(questionReportRepo.findIdsByQuestionIdIn(questionIds));
        }

        if (!reportIds.isEmpty()) {
            questionReportHistoryRepo.deleteByReportIdIn(new ArrayList<>(reportIds));
        }

        if (!attemptIds.isEmpty()) {
            questionReportRepo.deleteByAttemptIdIn(attemptIds);
            questionReviewEventRepo.deleteByAttemptIdIn(attemptIds);
            examAttemptAnswerRepo.deleteByAttemptIdIn(attemptIds);
            examAttemptRepo.deleteByExamId(examId);
        }

        if (!questionIds.isEmpty()) {
            questionReportRepo.deleteByQuestionIdIn(questionIds);
            optionRepo.deleteByQuestionIdIn(questionIds);
            questionRepo.deleteByExamId(examId);
        }

        String examTitle = existing.getTitle();
        Long examIdForAudit = existing.getId();
        examRepo.delete(existing);
        examFlowCacheService.evictExam(examId);
        publishSyncEvent(existing, "DELETE");
        examAuditService.log(
                ExamAuditService.ACTION_EXAM_DELETED,
                authenticatedUserService.getCurrentUserId(),
                null,
                ExamAuditService.TARGET_EXAM,
                examIdForAudit,
                examTitle,
                "Đề thi bị xóa vĩnh viễn khỏi DB");
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicExams", "publicExamDetail", "managedExams",
            "managedExamDetail"}, allEntries = true)
    public ExamResponse updateExamStatus(Long examId, OnlineExamStatus status) {
        OnlineExam existing = findExamOrThrow(examId);
        OnlineExamStatus previousStatus = existing.getStatus();
        existing.setStatus(status);
        OnlineExam saved = examRepo.save(existing);
        examFlowCacheService.evictExam(examId);

        examAuditService.log(
                ExamAuditService.ACTION_EXAM_STATUS_CHANGED,
                authenticatedUserService.getCurrentUserId(),
                null,
                ExamAuditService.TARGET_EXAM,
                examId,
                existing.getTitle(),
                "Trạng thái thay đổi: " + previousStatus + " → " + status);
        publishSyncEvent(saved, "UPSERT");
        return mapExamToResponse(saved, false, true, false, null, false);
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> getPublicExams() {
        List<OnlineExam> exams = examRepo.findByStatusOrderByCreatedAtDesc(OnlineExamStatus.PUBLISHED);
        return exams.stream().map(exam -> mapExamToResponse(exam, false, false, true, null, false)).toList();
    }

    @Transactional(readOnly = true)
    public ExamResponse getPublicExamById(Long examId) {
        OnlineExam exam = findExamOrThrow(examId);
        if (exam.getStatus() != OnlineExamStatus.PUBLISHED) {
            throw new ResponseStatusException(NOT_FOUND, "Exam not found");
        }
        return mapExamToResponse(exam, false, false, true, null, false);
    }

    @Transactional(readOnly = true)
    public ExamResponse mapPublicAttemptView(OnlineExam exam, Integer questionLimit, boolean premiumLocked) {
        return mapExamToResponse(exam, true, false, true, questionLimit, premiumLocked);
    }

    private OnlineExam findExamOrThrow(Long examId) {
        return examRepo.findById(examId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Exam not found"));
    }

    private OnlineExam buildExamEntity(OnlineExam exam, CreateExamRequest request) {
        exam.setTitle(request.getTitle());
        exam.setDescription(request.getDescription());
        exam.setDurationMinutes(request.getDurationMinutes());
        exam.setPassingScore(request.getPassingScore());
        Integer requestedMaxAttempts = request.getMaxAttempts();
        exam.setMaxAttempts(requestedMaxAttempts == null ? 100 : Math.max(1, requestedMaxAttempts));
        exam.setIsPremium(Boolean.TRUE.equals(request.getPremium()));
        exam.setTeaserQuestionCount(resolveTeaserQuestionCount(request.getTeaserQuestionCount()));

        Set<Tag> examTags = resolveExamTags(request.getTagIds(), request.getNewTags());
        if (exam.getTags() == null) {
            exam.setTags(new HashSet<>());
        }
        if (isTagSetChanged(exam.getTags(), examTags)) {
            exam.getTags().clear();
            exam.getTags().addAll(examTags);
        }

        if (request.getQuestions() != null) {
            exam.setTotalQuestions(request.getQuestions().size());
        } else {
            exam.setTotalQuestions(0);
        }

        return exam;
    }

    private Set<Tag> resolveExamTags(List<Long> tagIds, List<String> newTags) {
        Set<Tag> resolved = new HashSet<>();

        if (tagIds != null && !tagIds.isEmpty()) {
            Set<Long> uniqueIds = new HashSet<>(tagIds);
            List<Tag> existingTags = tagRepo.findAllById(uniqueIds);
            if (existingTags.size() != uniqueIds.size()) {
                throw new ResponseStatusException(BAD_REQUEST, "One or more tags do not exist");
            }
            resolved.addAll(existingTags);
        }

        if (newTags != null && !newTags.isEmpty()) {
            for (String rawName : newTags) {
                String normalizedName = tagService.normalizeTagName(rawName);
                Tag tag = tagRepo.findByName(normalizedName)
                        .orElseGet(() -> {
                            TagDto created = tagService.createTag(normalizedName);
                            return tagRepo.getReferenceById(created.getId());
                        });
                resolved.add(tag);
            }
        }

        return resolved;
    }

    private void upsertQuestions(OnlineExam exam, List<CreateExamRequest.QuestionDto> questionDtos) {
        if (questionDtos == null || questionDtos.isEmpty()) {
            return;
        }

        for (CreateExamRequest.QuestionDto qDto : questionDtos) {
            Question question = new Question();
            question.setExam(exam);
            question.setContent(qDto.getContent());
            question.setExplanation(qDto.getExplanation());
            question.setScoreWeight(qDto.getScoreWeight());

            Question savedQuestion = questionRepo.save(question);

            if (qDto.getOptions() == null || qDto.getOptions().isEmpty()) {
                continue;
            }

            for (CreateExamRequest.OptionDto optDto : qDto.getOptions()) {
                QuestionOption option = new QuestionOption();
                option.setQuestion(savedQuestion);
                option.setContent(optDto.getContent());
                option.setIsCorrect(Boolean.TRUE.equals(optDto.getIsCorrect()));
                optionRepo.save(option);
            }
        }
    }

    private void deleteQuestionsAndOptions(List<Question> questions) {
        if (questions.isEmpty()) {
            return;
        }

        List<Long> questionIds = questions.stream().map(BaseEntity::getId).toList();
        optionRepo.deleteByQuestionIdIn(questionIds);
        questionRepo.deleteByExamId(questions.getFirst().getExam().getId());
    }

    private boolean isQuestionTreeChanged(List<Question> existingQuestions,
                                          List<CreateExamRequest.QuestionDto> requestedQuestions) {
        List<CreateExamRequest.QuestionDto> requested = requestedQuestions == null ? List.of() : requestedQuestions;
        if (existingQuestions.size() != requested.size()) {
            return true;
        }

        if (existingQuestions.isEmpty()) {
            return false;
        }

        List<Long> questionIds = existingQuestions.stream().map(BaseEntity::getId).toList();
        Map<Long, List<QuestionOption>> optionsByQuestionId = new HashMap<>();
        for (QuestionOption option : optionRepo.findByQuestionIdInOrderByIdAsc(questionIds)) {
            optionsByQuestionId
                    .computeIfAbsent(option.getQuestion().getId(), key -> new ArrayList<>())
                    .add(option);
        }

        for (int i = 0; i < existingQuestions.size(); i++) {
            Question existingQuestion = existingQuestions.get(i);
            CreateExamRequest.QuestionDto requestedQuestion = requested.get(i);

            if (!Objects.equals(normalize(existingQuestion.getContent()), normalize(requestedQuestion.getContent()))) {
                return true;
            }

            if (!Objects.equals(normalize(existingQuestion.getExplanation()),
                    normalize(requestedQuestion.getExplanation()))) {
                return true;
            }

            if (!Objects.equals(existingQuestion.getScoreWeight(), requestedQuestion.getScoreWeight())) {
                return true;
            }

            List<QuestionOption> existingOptions = optionsByQuestionId.getOrDefault(existingQuestion.getId(),
                    List.of());
            List<CreateExamRequest.OptionDto> requestedOptions = requestedQuestion.getOptions() == null
                    ? List.of()
                    : requestedQuestion.getOptions();

            if (existingOptions.size() != requestedOptions.size()) {
                return true;
            }

            for (int optionIndex = 0; optionIndex < existingOptions.size(); optionIndex++) {
                QuestionOption existingOption = existingOptions.get(optionIndex);
                CreateExamRequest.OptionDto requestedOption = requestedOptions.get(optionIndex);

                if (!Objects.equals(normalize(existingOption.getContent()), normalize(requestedOption.getContent()))) {
                    return true;
                }

                boolean requestedIsCorrect = Boolean.TRUE.equals(requestedOption.getIsCorrect());
                if (!Objects.equals(existingOption.getIsCorrect(), requestedIsCorrect)) {
                    return true;
                }
            }
        }

        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isTagSetChanged(Set<Tag> currentTags, Set<Tag> requestedTags) {
        Set<Long> currentTagIds = currentTags.stream().map(Tag::getId).collect(HashSet::new, HashSet::add,
                HashSet::addAll);
        Set<Long> requestedTagIds = requestedTags.stream().map(Tag::getId).collect(HashSet::new, HashSet::add,
                HashSet::addAll);
        return !Objects.equals(currentTagIds, requestedTagIds);
    }

    private List<ExamResponse> mapExamListToSummary(List<OnlineExam> exams) {
        return exams.stream()
                .map(exam -> mapExamToResponse(exam, false, true, true, null, false))
                .toList();
    }

    private ExamResponse mapExamToResponse(OnlineExam exam,
                                           boolean includeQuestions,
                                           boolean includeAnswerKey,
                                           boolean includeTags,
                                           Integer questionLimit,
                                           boolean premiumLocked) {
        ExamResponse response = new ExamResponse();
        response.setId(exam.getId());
        response.setTitle(exam.getTitle());
        response.setDescription(exam.getDescription());
        response.setDurationMinutes(exam.getDurationMinutes());
        response.setPassingScore(exam.getPassingScore());
        response.setMaxAttempts(exam.getMaxAttempts());
        response.setTotalQuestions(exam.getTotalQuestions());
        response.setPremium(Boolean.TRUE.equals(exam.getIsPremium()));
        response.setTeaserQuestionCount(resolveTeaserQuestionCount(exam.getTeaserQuestionCount()));
        response.setPremiumLocked(premiumLocked);
        response.setStatus(exam.getStatus());
        response.setCreatedAt(exam.getCreatedAt());
        response.setModifiedAt(exam.getModifiedAt());

        if (includeTags && exam.getTags() != null && !exam.getTags().isEmpty()) {
            List<TagDto> tags = exam.getTags().stream()
                    .sorted(Comparator.comparing(Tag::getName))
                    .map(tagService::toDto)
                    .toList();
            response.setTags(tags);
        }

        if (!includeQuestions) {
            return response;
        }

        List<Question> questions = includeAnswerKey
                ? questionRepo.findByExamIdOrderByIdAsc(exam.getId())
                : questionRepo.findByExamIdAndIsHiddenFalseOrderByIdAsc(exam.getId());
        if (questions.isEmpty()) {
            return response;
        }

        if (questionLimit != null && questionLimit > 0 && questions.size() > questionLimit) {
            questions = new ArrayList<>(questions.subList(0, questionLimit));
        }

        List<Long> questionIds = questions.stream().map(BaseEntity::getId).toList();
        Map<Long, List<QuestionOption>> optionsByQuestionId = new HashMap<>();
        List<QuestionOption> options = optionRepo.findByQuestionIdInOrderByIdAsc(questionIds);
        for (QuestionOption option : options) {
            optionsByQuestionId
                    .computeIfAbsent(option.getQuestion().getId(), key -> new ArrayList<>())
                    .add(option);
        }

        List<ExamResponse.QuestionResponse> questionResponses = new ArrayList<>();
        for (Question question : questions) {
            ExamResponse.QuestionResponse questionResponse = new ExamResponse.QuestionResponse();
            questionResponse.setId(question.getId());
            questionResponse.setContent(question.getContent());
            questionResponse.setExplanation(question.getExplanation());
            questionResponse.setScoreWeight(question.getScoreWeight());
            questionResponse.setDifficulty(
                    question.getDifficulty() == null ? Question.Difficulty.MEDIUM : question.getDifficulty());

            List<ExamResponse.OptionResponse> optionResponses = new ArrayList<>();
            for (QuestionOption option : optionsByQuestionId.getOrDefault(question.getId(), List.of())) {
                ExamResponse.OptionResponse optionResponse = new ExamResponse.OptionResponse();
                optionResponse.setId(option.getId());
                optionResponse.setContent(option.getContent());
                optionResponse.setIsCorrect(includeAnswerKey ? option.getIsCorrect() : null);
                optionResponses.add(optionResponse);
            }

            questionResponse.setOptions(optionResponses);
            questionResponses.add(questionResponse);
        }

        response.setQuestions(questionResponses);
        return response;
    }

    private int resolveTeaserQuestionCount(Integer requestedValue) {
        if (requestedValue == null) {
            return DEFAULT_TEASER_QUESTION_COUNT;
        }

        return Math.max(MIN_TEASER_QUESTION_COUNT, Math.min(MAX_TEASER_QUESTION_COUNT, requestedValue));
    }

    @Transactional
    public void processAiExtractionResult(Long examId, String jsonResult) {
        // 1. Tìm đề thi gốc
        OnlineExam exam = examRepo.findById(examId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đề thi với ID: " + examId));

        try {
            // 2. Dịch chuỗi JSON thành List<AiQuestionDto>
            ObjectMapper mapper = new ObjectMapper();
            List<AiQuestionDto> parsedQuestions = mapper.readValue(jsonResult, new TypeReference<List<AiQuestionDto>>() {
            });

            List<Question> questionsToSave = new ArrayList<>();
            List<QuestionOption> optionsToSave = new ArrayList<>();

            // 3. Chuyển đổi DTO thành Entity
            for (AiQuestionDto dto : parsedQuestions) {
                Question question = new Question();
                question.setExam(exam);
                question.setContent(dto.getContent());
                question.setExplanation(dto.getExplanation());
                question.setScoreWeight(dto.getScoreWeight() != null ? dto.getScoreWeight() : 1.0);
                question.setDifficulty(Question.Difficulty.MEDIUM); // Mặc định là Trung bình
                question.setIsHidden(false);
                questionsToSave.add(question);

                if (dto.getOptions() != null) {
                    for (AiOptionDto optDto : dto.getOptions()) {
                        QuestionOption option = new QuestionOption();
                        option.setQuestion(question); // Nối khóa ngoại question_id
                        option.setContent(optDto.getContent());
                        option.setIsCorrect(optDto.isCorrect());
                        optionsToSave.add(option);
                    }
                }
            }

            // 4. Lưu đồng loạt vào Database cực nhanh (Batch Insert)
            questionRepo.saveAll(questionsToSave);
            optionRepo.saveAll(optionsToSave);

            // 5. Cập nhật lại tổng số câu hỏi cho Đề thi (Vẫn giữ status là DRAFT để Admin duyệt lại)
            exam.setTotalQuestions(questionsToSave.size());
            examRepo.save(exam);
            publishSyncEvent(exam, "UPSERT");
            log.info("Đã lưu thành công {} câu hỏi vào DB cho Đề thi ID: {}", questionsToSave.size(), examId);

        } catch (Exception e) {
            log.error("Lỗi khi parse và lưu JSON từ AI cho Exam ID {}: {}", examId, e.getMessage());
            throw new RuntimeException("Không thể lưu dữ liệu AI vào Database", e);
        }
    }

    private void publishSyncEvent(OnlineExam exam, String action) {
        try {
            List<String> tagNames = exam.getTags() != null
                    ? exam.getTags().stream().map(Tag::getName).toList()
                    : new ArrayList<>();

            ExamSyncEvent syncEvent = ExamSyncEvent.builder()
                    .id(exam.getId())
                    .title(exam.getTitle())
                    .status(exam.getStatus().name())
                    .isPremium(exam.getIsPremium())
                    .tags(tagNames)
                    .action(action) // "UPSERT" hoặc "DELETE"
                    .build();

            rabbitMQEventPublisher.publishExamSyncEvent(syncEvent);
        } catch (Exception e) {
            log.error("Lỗi khi bắn sự kiện đồng bộ Elasticsearch cho Exam ID: {}", exam.getId(), e);
        }
    }
}