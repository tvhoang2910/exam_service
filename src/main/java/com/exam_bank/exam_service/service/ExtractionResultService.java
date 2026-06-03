package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.dto.message.AiExtractionResultEvent;
import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.entity.Question;
import com.exam_bank.exam_service.entity.Question.Difficulty;
import com.exam_bank.exam_service.entity.QuestionOption;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadRequest;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadStatus;
import com.exam_bank.exam_service.feature.upload.repository.ExamUploadRequestRepository;
import com.exam_bank.exam_service.repository.OnlineExamRepository;
import com.exam_bank.exam_service.repository.QuestionOptionRepository;
import com.exam_bank.exam_service.repository.QuestionRepository;
import com.exam_bank.exam_service.util.AiJsonNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtractionResultService {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final ExamUploadRequestRepository uploadRequestRepository;
    private final OnlineExamRepository examRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final ExamSseService examSseService;
    private final AdminAlertPublisher adminAlertPublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processExtractionResult(AiExtractionResultEvent event) {
        Long uploadRequestId = event.getUploadRequestId();
        Long examId = event.getExamId();

        ExamUploadRequest upload = uploadRequestRepository.findById(uploadRequestId)
                .orElseThrow(() -> new IllegalStateException(
                        "ExamUploadRequest not found: id=" + uploadRequestId));

        ExamUploadStatus current = upload.getStatus();
        if (current == ExamUploadStatus.EXTRACTED
                || current == ExamUploadStatus.EXTRACT_FAILED
                || current == ExamUploadStatus.REJECTED) {
            log.warn("Idempotency skip: uploadRequestId={} already in terminal status={}",
                    uploadRequestId, current);
            return;
        }

        OnlineExam exam = examRepository.findById(examId)
                .orElseThrow(() -> new IllegalStateException("OnlineExam not found: id=" + examId));

        if (Boolean.FALSE.equals(event.getSuccessFlag())) {
            handleFailure(upload, exam, event.getErrorMessage());
            return;
        }

        handleSuccess(upload, exam, event.getAiJsonResult());
    }

        @Transactional
        public void createPlaceholderForUpload(Long uploadRequestId, Long examId) {
        ExamUploadRequest upload = uploadRequestRepository.findById(uploadRequestId)
            .orElseThrow(() -> new IllegalStateException("ExamUploadRequest not found: id=" + uploadRequestId));

        if (upload.getStatus() == ExamUploadStatus.EXTRACTED) {
            log.info("createPlaceholderForUpload: upload {} already extracted, skipping", uploadRequestId);
            return;
        }

        OnlineExam exam = examRepository.findById(examId)
            .orElseThrow(() -> new IllegalStateException("OnlineExam not found: id=" + examId));

        ParsedQuestion fallback = new ParsedQuestion();
        fallback.content = "[MANUAL-PLACEHOLDER] Exam created from failed AI extraction";
        fallback.explanation = "Placeholder question inserted by admin to recover from AI extraction failure.";

        int saved = persistQuestions(exam, List.of(fallback));
        exam.setTotalQuestions((exam.getTotalQuestions() == null ? 0 : exam.getTotalQuestions()) + saved);
        examRepository.save(exam);

        upload.setStatus(ExamUploadStatus.EXTRACTED);
        upload.setExtractedExamId(exam.getId());
        upload.setExtractionError(null);
        uploadRequestRepository.save(upload);

        examSseService.onAiExtractionCompleted(
            exam.getId(),
            upload.getId(),
            exam.getId(),
            upload.getReviewedBy(),
            true,
            "Placeholder extraction completed by admin");

        notifyReviewer(upload, true, null);
        broadcastSseToUploader(upload.getUploaderId(), "AI_EXTRACTION_SUCCESS_MANUAL",
            Map.of("uploadRequestId", upload.getId(), "extractedExamId", exam.getId(), "savedQuestions", saved));
        }

    private void handleFailure(ExamUploadRequest upload, OnlineExam exam, String errorMessage) {
        String truncated = truncate(errorMessage, MAX_ERROR_LENGTH);
        upload.setStatus(ExamUploadStatus.EXTRACT_FAILED);
        upload.setExtractionError(truncated);
        uploadRequestRepository.save(upload);
        log.warn("AI extraction failed: uploadRequestId={} examId={} error={}",
                upload.getId(), exam.getId(), truncated);
        examSseService.onAiExtractionCompleted(
                exam.getId(),
                upload.getId(),
                upload.getExtractedExamId(),
                upload.getReviewedBy(),
                false,
                truncated == null ? "AI extraction failed" : truncated);
        notifyReviewer(upload, false, truncated);
        broadcastSseToUploader(upload.getUploaderId(), "AI_EXTRACTION_FAILED",
                Map.of(
                        "uploadRequestId", upload.getId(),
                        "examId", exam.getId(),
                        "errorMessage", truncated == null ? "" : truncated));
    }

    private void handleSuccess(ExamUploadRequest upload, OnlineExam exam, String aiJsonResult) {
        List<ParsedQuestion> parsed;
        try {
            parsed = parseAiJson(aiJsonResult);
        } catch (Exception ex) {
            String preview = aiJsonResult == null ? "" : aiJsonResult.replaceAll("\\s+", " ");
            if (preview.length() > 1024)
                preview = preview.substring(0, 1024) + "...";
            log.error("Failed to parse AI JSON for uploadRequestId={} examId={} (preview={}): {}",
                    upload.getId(), exam.getId(), preview, ex.getMessage(), ex);
            handleFailure(upload, exam, "Failed to parse AI JSON: " + ex.getMessage());
            return;
        }

        if (parsed == null || parsed.isEmpty()) {
            handleFailure(upload, exam, "AI returned 0 questions");
            return;
        }

        int savedCount = persistQuestions(exam, parsed);
        if (savedCount == 0) {
            handleFailure(upload, exam, "AI returned questions but all were invalid (blank content)");
            return;
        }

        exam.setTotalQuestions(savedCount);
        examRepository.save(exam);

        upload.setStatus(ExamUploadStatus.EXTRACTED);
        upload.setExtractedExamId(exam.getId());
        upload.setExtractionError(null);
        uploadRequestRepository.save(upload);

        log.info("AI extraction succeeded: uploadRequestId={} examId={} savedQuestions={}",
                upload.getId(), exam.getId(), savedCount);

        examSseService.onAiExtractionCompleted(
                exam.getId(),
                upload.getId(),
                exam.getId(),
                upload.getReviewedBy(),
                true,
                "AI extraction completed successfully");
        notifyReviewer(upload, true, null);

        broadcastSseToUploader(upload.getUploaderId(), "AI_EXTRACTION_SUCCESS",
                Map.of(
                        "uploadRequestId", upload.getId(),
                        "extractedExamId", exam.getId(),
                        "savedQuestions", savedCount,
                        "message", "AI extraction completed successfully"));
    }

    private int persistQuestions(OnlineExam exam, List<ParsedQuestion> parsedQuestions) {
        int saved = 0;
        for (ParsedQuestion pq : parsedQuestions) {
            if (pq == null || pq.content == null || pq.content.trim().isEmpty()) {
                log.warn("Skipping question with blank content for examId={}", exam.getId());
                continue;
            }
            Question question = new Question();
            question.setExam(exam);
            question.setContent(pq.content.trim());
            question.setExplanation(pq.explanation);
            question.setDifficulty(parseDifficulty(pq.difficulty));
            Question savedQuestion = questionRepository.save(question);

            if (pq.options != null) {
                for (ParsedOption po : pq.options) {
                    if (po == null || po.content == null || po.content.trim().isEmpty()) {
                        continue;
                    }
                    QuestionOption option = new QuestionOption();
                    option.setQuestion(savedQuestion);
                    option.setContent(po.content.trim());
                    option.setIsCorrect(Boolean.TRUE.equals(po.isCorrect));
                    questionOptionRepository.save(option);
                }
            }
            saved++;
        }
        return saved;
    }

    private List<ParsedQuestion> parseAiJson(String rawJson) {
        String json = AiJsonNormalizer.normalizeQuestionArray(rawJson);
        if (json.isEmpty()) {
            return List.of();
        }
        
        try {
            return objectMapper.readValue(json, new TypeReference<List<ParsedQuestion>>() {
            });
        } catch (Exception ex) {
            // If parsing fails, try escaping remaining invalid sequences so they become literal backslashes.
            // Pattern: backslash followed by any char that's not a valid JSON escape.
            String sanitized = json.replaceAll("\\\\([^\"\\\\\\\\/ bfnrtu])", "\\\\\\\\$1");
            log.warn("JSON parse failed, retrying with sanitized content (escaped invalid escapes): {}",
                    ex.getMessage());
            try {
                return objectMapper.readValue(sanitized, new TypeReference<List<ParsedQuestion>>() {
                });
            } catch (Exception ex2) {
                log.error("JSON parse failed even after sanitization. Original error: {} Sanitized error: {}",
                        ex.getMessage(), ex2.getMessage());
                // Pragmatic fallback: if AI output cannot be parsed to structured questions,
                // create a single placeholder question containing a truncated preview
                // of the raw AI output so the upload can still produce an exam for reviewers.
                String preview = json == null ? "" : json.replaceAll("\\s+", " ");
                if (preview.length() > 2000) preview = preview.substring(0, 2000) + "...";
                ParsedQuestion fallback = new ParsedQuestion();
                // Remove surrounding JSON punctuation if present to make preview more readable
                String readable = preview.replaceAll("^[\\n\\r\\t\\[\\]\\{\\s]*|[\\n\\r\\t\\[\\]\\}\\s]*$", "");
                fallback.content = "[AUTO-EXTRACTED] " + readable;
                fallback.explanation = "AI produced unparsable JSON; original output attached as content.";
                return List.of(fallback);
            }
        }
    }

    private Difficulty parseDifficulty(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Difficulty.MEDIUM;
        }
        try {
            return Difficulty.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Difficulty.MEDIUM;
        }
    }

    private String truncate(String s, int max) {
        if (s == null)
            return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private void broadcastSseToUploader(Long uploaderId, String eventType, Map<String, Object> data) {
        if (uploaderId == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", eventType);
        payload.putAll(data);
        examSseService.sendToUser(uploaderId, "exam", payload);
    }

    private void notifyReviewer(ExamUploadRequest upload, boolean isSuccess, String errorMessage) {
        Long reviewerId = upload.getReviewedBy();
        if (reviewerId == null) {
            return;
        }
        adminAlertPublisher.publishUploadExtractionCompletedAlert(
                upload.getId(),
                upload.getTitle(),
                reviewerId,
                isSuccess,
                errorMessage);
    }

    public static class ParsedQuestion {
        public String content;
        public String explanation;
        public String difficulty;
        public Double scoreWeight;
        public List<ParsedOption> options;
    }

    public static class ParsedOption {
        public String content;
        public Boolean isCorrect;
    }
}
