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

    private void handleFailure(ExamUploadRequest upload, OnlineExam exam, String errorMessage) {
        String truncated = truncate(errorMessage, MAX_ERROR_LENGTH);
        upload.setStatus(ExamUploadStatus.EXTRACT_FAILED);
        upload.setExtractionError(truncated);
        uploadRequestRepository.save(upload);
        log.warn("AI extraction failed: uploadRequestId={} examId={} error={}",
                upload.getId(), exam.getId(), truncated);
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
            log.error("Failed to parse AI JSON for uploadRequestId={} examId={}: {}",
                    upload.getId(), exam.getId(), ex.getMessage(), ex);
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
        if (rawJson == null) {
            return List.of();
        }
        String json = rawJson.trim();
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            if (firstNewline > 0) {
                json = json.substring(firstNewline + 1);
            } else {
                json = json.substring(3);
            }
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        json = json.trim();
        if (json.isEmpty()) {
            return List.of();
        }
        return objectMapper.readValue(json, new TypeReference<List<ParsedQuestion>>() {});
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
        if (s == null) return null;
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

    public static class ParsedQuestion {
        public String content;
        public String explanation;
        public String difficulty;
        public List<ParsedOption> options;
    }

    public static class ParsedOption {
        public String content;
        public Boolean isCorrect;
    }
}
