package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.dto.CreateExamRequest;
import com.exam_bank.exam_service.dto.ExamResponse;
import com.exam_bank.exam_service.dto.TagDto;
import com.exam_bank.exam_service.dto.internal.AiQuestionDto;
import com.exam_bank.exam_service.dto.internal.AiOptionDto;
import com.exam_bank.exam_service.dto.message.ExamSyncEvent;
import com.exam_bank.exam_service.entity.*;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadStatus;
import com.exam_bank.exam_service.feature.upload.repository.ExamUploadRequestRepository;
import com.exam_bank.exam_service.feature.reporting.repository.QuestionReportHistoryRepository;
import com.exam_bank.exam_service.feature.reporting.repository.QuestionReportRepository;
import com.exam_bank.exam_service.repository.*;
import com.exam_bank.exam_service.util.AiJsonNormalizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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
    private final Sm2RecordRepository sm2RecordRepo;
    private final ExamAttemptAnswerRepository examAttemptAnswerRepo;
    private final QuestionReviewEventRepository questionReviewEventRepo;
    private final QuestionReportRepository questionReportRepo;
    private final QuestionReportHistoryRepository questionReportHistoryRepo;
    private final ExamUploadRequestRepository examUploadRequestRepository;
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
        log.info("TRẠM 1 - Dữ liệu từ Postman gửi lên: {}", request.getNewTags());
        OnlineExam exam = buildExamEntity(new OnlineExam(), request);
        exam.setSource(OnlineExamSource.MANUAL_CREATED);
        exam.setStatus(OnlineExamStatus.DRAFT);
        OnlineExam savedExam = examRepo.save(exam);
        log.info("TRẠM 2 - Tags đã lưu thành công vào PostgreSQL: {}", savedExam.getTags());
        upsertQuestions(savedExam, request.getQuestions());
        examFlowCacheService.evictExam(savedExam.getId());
        publishSyncEvent(savedExam, "UPSERT");
        return mapExamToResponse(savedExam, false, true, true, null, false);
    }

    @Transactional
    @CacheEvict(cacheNames = { "publicExams", "publicExamDetail", "managedExams",
            "managedExamDetail" }, allEntries = true)
    public ExamResponse createUploadedDraftExam(String title, String objectName, String contentType) {
        if (!StringUtils.hasText(title)) {
            throw new ResponseStatusException(BAD_REQUEST, "Exam title must not be empty");
        }
        if (!StringUtils.hasText(objectName)) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded file reference must not be empty");
        }

        OnlineExam exam = new OnlineExam();
        exam.setTitle(title.trim());
        exam.setSource(OnlineExamSource.AI_EXTRACTED);
        exam.setStatus(OnlineExamStatus.DRAFT);
        exam.setOriginalFileUrl(objectName);
        exam.setOriginalFileType(contentType);
        exam.setTotalQuestions(0);

        OnlineExam savedExam = examRepo.save(exam);
        examFlowCacheService.evictExam(savedExam.getId());

        return mapExamToResponse(savedExam, false, true, true, null, false);
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> getManagedExams() {
        List<OnlineExam> exams = isCurrentUserAdmin()
                ? examRepo.findAllByOrderByCreatedAtDesc()
                : examRepo.findByCreatedByOrderByCreatedAtDesc(String.valueOf(authenticatedUserService.getCurrentUserId()));
        return mapExamListToSummary(filterHiddenAiExtractionDrafts(exams));
    }

    @Transactional(readOnly = true)
    public ExamResponse getManagedExamById(Long examId) {
        OnlineExam exam = findManagedExamOrThrow(examId);
        return mapExamToResponse(exam, true, true, true, null, false);
    }

    @Transactional
    @CacheEvict(cacheNames = {"publicExams", "publicExamDetail", "managedExams",
            "managedExamDetail"}, allEntries = true)
    public ExamResponse updateExam(Long examId, CreateExamRequest request) {
        OnlineExam existing = findManagedExamOrThrow(examId);

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
        OnlineExam existing = findManagedExamOrThrow(examId);
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
            sm2RecordRepo.deleteByQuestionIdIn(questionIds);
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
        OnlineExam existing = findManagedExamOrThrow(examId);

        if (status == OnlineExamStatus.PUBLISHED) {
            Integer total = existing.getTotalQuestions();
            if (total == null || total <= 0) {
                log.warn("Reject publish exam {}: totalQuestions={}", examId, total);
                throw new ResponseStatusException(BAD_REQUEST, "Cannot publish exam with zero questions");
            }
        }

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

    private OnlineExam findManagedExamOrThrow(Long examId) {
        OnlineExam exam = findExamOrThrow(examId);
        if (isCurrentUserAdmin()) {
            ensureVisibleManagedExam(exam);
            return exam;
        }

        String currentUserId = String.valueOf(authenticatedUserService.getCurrentUserId());
        if (!currentUserId.equals(exam.getCreatedBy())) {
            throw new ResponseStatusException(NOT_FOUND, "Exam not found");
        }
        ensureVisibleManagedExam(exam);
        return exam;
    }

    private List<OnlineExam> filterHiddenAiExtractionDrafts(List<OnlineExam> exams) {
        if (exams.isEmpty()) {
            return exams;
        }

        Set<Long> aiExtractedIds = exams.stream()
                .filter(this::isAiExtractionPlaceholder)
                .map(OnlineExam::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (aiExtractedIds.isEmpty()) {
            return exams;
        }

        Set<Long> hiddenExamIds = examUploadRequestRepository.findHiddenManagedExamIds(
                aiExtractedIds,
                List.of(ExamUploadStatus.EXTRACTED));
        if (hiddenExamIds.isEmpty()) {
            return exams;
        }

        return exams.stream()
                .filter(exam -> !hiddenExamIds.contains(exam.getId()))
                .toList();
    }

    private void ensureVisibleManagedExam(OnlineExam exam) {
        if (!isAiExtractionPlaceholder(exam)) {
            return;
        }

        examUploadRequestRepository.findByExtractedExamId(exam.getId())
                .filter(upload -> upload.getStatus() == ExamUploadStatus.EXTRACTED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Exam not found"));
    }

    private boolean isAiExtractionPlaceholder(OnlineExam exam) {
        return exam.getSource() == OnlineExamSource.AI_EXTRACTED
                && (exam.getTotalQuestions() == null || exam.getTotalQuestions() <= 0);
    }

    private boolean isCurrentUserAdmin() {
        return authenticatedUserService.currentUserHasRole("ADMIN");
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

        // 1. Xử lý các Tag đã có sẵn qua ID
        if (tagIds != null && !tagIds.isEmpty()) {
            Set<Long> uniqueIds = new HashSet<>(tagIds);
            List<Tag> existingTags = tagRepo.findAllById(uniqueIds);
            if (existingTags.size() != uniqueIds.size()) {
                throw new ResponseStatusException(BAD_REQUEST, "One or more tags do not exist");
            }
            resolved.addAll(existingTags);
        }

        // 2. Xử lý các Tag gửi dạng chữ (newTags)
        if (newTags != null && !newTags.isEmpty()) {
            for (String rawName : newTags) {
                if (rawName == null || rawName.trim().isEmpty()) continue;

                // Tận dụng hàm normalize có sẵn của bạn
                String normalizedName = tagService.normalizeTagName(rawName);

                // Tìm và lưu trực tiếp Entity thật, KHÔNG dùng getReferenceById để tránh lỗi Proxy
                Tag tag = tagRepo.findByName(normalizedName)
                        .orElseGet(() -> {
                            Tag newTag = new Tag();
                            newTag.setName(normalizedName);
                            // Lưu trực tiếp xuống Database và lấy ra Object thật
                            return tagRepo.save(newTag);
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
            validateQuestionDto(qDto);
            QuestionType questionType = resolveQuestionType(qDto.getQuestionType());
            Question question = new Question();
            question.setExam(exam);
            question.setContent(qDto.getContent());
            question.setQuestionType(questionType);
            question.setExplanation(qDto.getExplanation());
            question.setScoreWeight(resolveQuestionScore(qDto));
            question.setSampleAnswer(qDto.getSampleAnswer());
            question.setGradingGuide(qDto.getGradingGuide());

            Question savedQuestion = questionRepo.save(question);

            if (questionType == QuestionType.ESSAY || qDto.getOptions() == null || qDto.getOptions().isEmpty()) {
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
        sm2RecordRepo.deleteByQuestionIdIn(questionIds);
        optionRepo.deleteByQuestionIdIn(questionIds);
        questionRepo.deleteByExamId(questions.getFirst().getExam().getId());
    }

    private void validateQuestionDto(CreateExamRequest.QuestionDto question) {
        if (question == null || !StringUtils.hasText(question.getContent())) {
            throw new ResponseStatusException(BAD_REQUEST, "Question content must not be empty");
        }

        QuestionType questionType = resolveQuestionType(question.getQuestionType());
        List<CreateExamRequest.OptionDto> options = question.getOptions() == null ? List.of() : question.getOptions();

        if (questionType == QuestionType.ESSAY) {
            if (!options.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "Essay questions must not define options");
            }
            if (!StringUtils.hasText(question.getSampleAnswer())) {
                throw new ResponseStatusException(BAD_REQUEST, "Essay questions must define a sample answer");
            }
            if (!StringUtils.hasText(question.getGradingGuide())) {
                throw new ResponseStatusException(BAD_REQUEST, "Essay questions must define a grading guide");
            }
            return;
        }

        if (options.size() < 2) {
            throw new ResponseStatusException(BAD_REQUEST, "Multiple-choice questions must have at least 2 options");
        }

        boolean hasCorrectOption = false;
        for (CreateExamRequest.OptionDto option : options) {
            if (option == null || !StringUtils.hasText(option.getContent())) {
                throw new ResponseStatusException(BAD_REQUEST, "Option content must not be empty");
            }
            hasCorrectOption = hasCorrectOption || Boolean.TRUE.equals(option.getIsCorrect());
        }

        if (!hasCorrectOption) {
            throw new ResponseStatusException(BAD_REQUEST, "Multiple-choice questions must have at least one correct option");
        }
    }

    private QuestionType resolveQuestionType(QuestionType questionType) {
        return questionType == null ? QuestionType.MULTIPLE_CHOICE : questionType;
    }

    private double resolveQuestionScore(CreateExamRequest.QuestionDto question) {
        return resolveQuestionScore(question.getScore(), question.getScoreWeight());
    }

    private double resolveQuestionScore(Double score, Double scoreWeight) {
        Double resolved = score != null ? score : scoreWeight;
        if (resolved == null || resolved <= 0) {
            return 1.0;
        }
        return resolved;
    }

    private QuestionType parseQuestionType(String rawQuestionType, List<AiOptionDto> options) {
        if (StringUtils.hasText(rawQuestionType)) {
            try {
                return QuestionType.valueOf(rawQuestionType.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall back to option presence below
            }
        }
        return options == null || options.isEmpty() ? QuestionType.ESSAY : QuestionType.MULTIPLE_CHOICE;
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

            if (resolveQuestionType(existingQuestion.getQuestionType()) != resolveQuestionType(requestedQuestion.getQuestionType())) {
                return true;
            }

            if (!Objects.equals(normalize(existingQuestion.getExplanation()),
                    normalize(requestedQuestion.getExplanation()))) {
                return true;
            }

            if (!Objects.equals(existingQuestion.getScoreWeight(), resolveQuestionScore(requestedQuestion))) {
                return true;
            }

            if (!Objects.equals(normalize(existingQuestion.getSampleAnswer()),
                    normalize(requestedQuestion.getSampleAnswer()))) {
                return true;
            }

            if (!Objects.equals(normalize(existingQuestion.getGradingGuide()),
                    normalize(requestedQuestion.getGradingGuide()))) {
                return true;
            }

            List<QuestionOption> existingOptions = optionsByQuestionId.getOrDefault(existingQuestion.getId(),
                    List.of());
            List<CreateExamRequest.OptionDto> requestedOptions = requestedQuestion.getOptions() == null
                    ? List.of()
                    : requestedQuestion.getOptions();

            if (resolveQuestionType(requestedQuestion.getQuestionType()) == QuestionType.ESSAY) {
                requestedOptions = List.of();
            }

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
            questionResponse.setQuestionType(resolveQuestionType(question.getQuestionType()));
            questionResponse.setExplanation(question.getExplanation());
            questionResponse.setScore(question.getScoreWeight());
            questionResponse.setScoreWeight(question.getScoreWeight());
            questionResponse.setSampleAnswer(question.getSampleAnswer());
            questionResponse.setGradingGuide(question.getGradingGuide());
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
            String normalizedJson = AiJsonNormalizer.normalizeQuestionArray(jsonResult);
            
            List<AiQuestionDto> parsedQuestions;
            try {
                parsedQuestions = mapper.readValue(normalizedJson, new TypeReference<List<AiQuestionDto>>() {
                });
            } catch (Exception ex) {
                // If parsing fails, try removing remaining invalid escape sequences
                // Pattern: backslash followed by any char that's not a valid JSON escape
                String sanitized = normalizedJson.replaceAll("\\\\([^\"\\\\\\\\/ bfnrtu])", "$1");
                log.warn("JSON parse failed for examId={}, retrying with sanitized content: {}", 
                        examId, ex.getMessage());
                try {
                    parsedQuestions = mapper.readValue(sanitized, new TypeReference<List<AiQuestionDto>>() {
                    });
                } catch (Exception ex2) {
                    log.error("JSON parse failed even after sanitization for examId={}. Error: {}", 
                            examId, ex2.getMessage(), ex2);
                    throw new RuntimeException("Failed to parse AI JSON even after sanitization", ex2);
                }
            }

            List<Question> questionsToSave = new ArrayList<>();
            List<QuestionOption> optionsToSave = new ArrayList<>();

            // 3. Chuyển đổi DTO thành Entity
            for (AiQuestionDto dto : parsedQuestions) {
                QuestionType questionType = parseQuestionType(dto.getQuestionType(), dto.getOptions());
                Question question = new Question();
                question.setExam(exam);
                question.setContent(dto.getContent());
                question.setQuestionType(questionType);
                question.setExplanation(dto.getExplanation());
                question.setScoreWeight(resolveQuestionScore(dto.getScore(), dto.getScoreWeight()));
                question.setSampleAnswer(dto.getSampleAnswer());
                question.setGradingGuide(dto.getGradingGuide());
                question.setDifficulty(parseAiDifficulty(dto.getDifficulty()));
                question.setIsHidden(false);
                questionsToSave.add(question);

                if (questionType == QuestionType.MULTIPLE_CHOICE && dto.getOptions() != null) {
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
            log.error("Lỗi khi parse và lưu JSON từ AI cho Exam ID {}: {}", examId, e.getMessage(), e);
            throw new RuntimeException("Không thể lưu dữ liệu AI vào Database", e);
        }
    }

    private Question.Difficulty parseAiDifficulty(String rawDifficulty) {
        if (rawDifficulty == null || rawDifficulty.isBlank()) {
            return Question.Difficulty.MEDIUM;
        }
        try {
            return Question.Difficulty.valueOf(rawDifficulty.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Question.Difficulty.MEDIUM;
        }
    }

    private void publishSyncEvent(OnlineExam exam, String action) {
        try {
            List<String> tagNames = exam.getTags() != null
                    ? exam.getTags().stream().map(Tag::getName).toList()
                    : new ArrayList<>();
            log.info("TRẠM 3 - Chuẩn bị bắn sang RabbitMQ. Danh sách Tags là: {}", tagNames);
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
