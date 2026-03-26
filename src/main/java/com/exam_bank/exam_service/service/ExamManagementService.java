package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.dto.CreateExamRequest;
import com.exam_bank.exam_service.dto.ExamResponse;
import com.exam_bank.exam_service.entity.*;
import com.exam_bank.exam_service.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ExamManagementService {

    private final OnlineExamRepository examRepo;
    private final QuestionRepository questionRepo;
    private final QuestionOptionRepository optionRepo;
    private final TagRepository tagRepo; // Đã tiêm TagRepository vào đây

    @Transactional
    public ExamResponse createManualExam(CreateExamRequest request) {
        OnlineExam exam = buildExamEntity(new OnlineExam(), request);
        exam.setSource(OnlineExamSource.MANUAL_CREATED);
        exam.setStatus(OnlineExamStatus.DRAFT);
        OnlineExam savedExam = examRepo.save(exam);
        upsertQuestions(savedExam, request.getQuestions());
        return mapExamToResponse(savedExam, true);
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> getManagedExams() {
        List<OnlineExam> exams = examRepo.findAllByOrderByCreatedAtDesc();
        return mapExamListToSummary(exams);
    }

    @Transactional(readOnly = true)
    public ExamResponse getManagedExamById(Long examId) {
        OnlineExam exam = findExamOrThrow(examId);
        return mapExamToResponse(exam, true);
    }

    @Transactional
    public ExamResponse updateExam(Long examId, CreateExamRequest request) {
        OnlineExam existing = findExamOrThrow(examId);
        OnlineExam updatedExam = examRepo.save(buildExamEntity(existing, request));

        List<Question> existingQuestions = questionRepo.findByExamIdOrderByIdAsc(examId);
        deleteQuestionsAndOptions(existingQuestions);
        upsertQuestions(updatedExam, request.getQuestions());

        return mapExamToResponse(updatedExam, true);
    }

    @Transactional
    public void deleteExam(Long examId) {
        OnlineExam existing = findExamOrThrow(examId);
        List<Question> existingQuestions = questionRepo.findByExamIdOrderByIdAsc(examId);
        deleteQuestionsAndOptions(existingQuestions);
        examRepo.delete(existing);
    }

    @Transactional
    public ExamResponse updateExamStatus(Long examId, OnlineExamStatus status) {
        OnlineExam existing = findExamOrThrow(examId);
        existing.setStatus(status);
        OnlineExam saved = examRepo.save(existing);
        return mapExamToResponse(saved, false);
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> getPublicExams() {
        List<OnlineExam> exams = examRepo.findByStatusOrderByCreatedAtDesc(OnlineExamStatus.PUBLISHED);
        return mapExamListToSummary(exams);
    }

    @Transactional(readOnly = true)
    public ExamResponse getPublicExamById(Long examId) {
        OnlineExam exam = findExamOrThrow(examId);
        if (exam.getStatus() != OnlineExamStatus.PUBLISHED) {
            throw new ResponseStatusException(NOT_FOUND, "Exam not found");
        }
        return mapExamToResponse(exam, true);
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

        // --- BỘ XỬ LÝ TAGS THÔNG MINH ---
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            Set<Tag> examTags = new HashSet<>();
            for (String tagName : request.getTags()) {
                String normalizedTagName = tagName.trim().toLowerCase(); // Chuẩn hóa về chữ thường
                if (normalizedTagName.isEmpty()) continue;

                // Dò tìm trong Database, chưa có thì tạo mới, có rồi thì lấy ra dùng
                Tag tag = tagRepo.findByName(normalizedTagName)
                        .orElseGet(() -> {
                            Tag newTag = new Tag();
                            newTag.setName(normalizedTagName);
                            return tagRepo.save(newTag);
                        });
                examTags.add(tag);
            }
            exam.setTags(examTags); // Gắn tập hợp Tag vào Đề thi
        } else {
            // Nếu update mà người dùng xóa hết tag, thì dọn sạch danh sách tag của đề thi này
            if (exam.getTags() != null) {
                exam.getTags().clear();
            }
        }
        // --------------------------------

        if (request.getQuestions() != null) {
            exam.setTotalQuestions(request.getQuestions().size());
        } else {
            exam.setTotalQuestions(0);
        }

        return exam;
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

    private List<ExamResponse> mapExamListToSummary(List<OnlineExam> exams) {
        return exams.stream()
                .map(exam -> mapExamToResponse(exam, false))
                .toList();
    }

    private ExamResponse mapExamToResponse(OnlineExam exam, boolean includeQuestions) {
        ExamResponse response = new ExamResponse();
        response.setId(exam.getId());
        response.setTitle(exam.getTitle());
        response.setDescription(exam.getDescription());
        response.setDurationMinutes(exam.getDurationMinutes());
        response.setPassingScore(exam.getPassingScore());
        response.setTotalQuestions(exam.getTotalQuestions());
        response.setStatus(exam.getStatus());
        response.setCreatedAt(exam.getCreatedAt());
        response.setModifiedAt(exam.getModifiedAt());

        // --- DỊCH TỪ SET<TAG> TRONG DB THÀNH LIST<STRING> CHO FRONTEND ---
        if (exam.getTags() != null && !exam.getTags().isEmpty()) {
            List<String> tagNames = exam.getTags().stream()
                    .map(Tag::getName)
                    .toList();
            response.setTags(tagNames);
        }
        // ----------------------------------------------------------------

        if (!includeQuestions) {
            return response;
        }

        List<Question> questions = questionRepo.findByExamIdOrderByIdAsc(exam.getId());
        if (questions.isEmpty()) {
            return response;
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

            List<ExamResponse.OptionResponse> optionResponses = new ArrayList<>();
            for (QuestionOption option : optionsByQuestionId.getOrDefault(question.getId(), List.of())) {
                ExamResponse.OptionResponse optionResponse = new ExamResponse.OptionResponse();
                optionResponse.setId(option.getId());
                optionResponse.setContent(option.getContent());
                optionResponse.setIsCorrect(option.getIsCorrect());
                optionResponses.add(optionResponse);
            }

            questionResponse.setOptions(optionResponses);
            questionResponses.add(questionResponse);
        }

        response.setQuestions(questionResponses);
        return response;
    }
}