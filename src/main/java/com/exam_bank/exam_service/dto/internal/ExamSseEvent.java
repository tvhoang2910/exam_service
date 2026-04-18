package com.exam_bank.exam_service.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSseEvent {

    public enum EventType {
        EXAM_SUBMITTED,
        ATTEMPT_STARTED,
        ATTEMPT_ENDED,
        SNAPSHOT,
        AI_EXTRACTION_SUCCESS,
        AI_EXTRACTION_FAILED
    }

    private EventType eventType;
    private Long attemptId;
    private Long examId;
    private String examTitle;
    private Long userId;
    private int activeAttemptCount;
    private int totalSubmissionsToday;
    private long timestamp;
    private String message;

    // --- CÁC HÀM TẠO SỰ KIỆN SỬ DỤNG BUILDER (SIÊU GỌN GÀNG) ---

    public static ExamSseEvent snapshot(int activeAttemptCount, int totalSubmissionsToday) {
        return ExamSseEvent.builder()
                .eventType(EventType.SNAPSHOT)
                .activeAttemptCount(activeAttemptCount)
                .totalSubmissionsToday(totalSubmissionsToday)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ExamSseEvent submitted(Long attemptId, Long examId, String examTitle,
                                         Long userId, int activeAttemptCount, int totalSubmissionsToday) {
        return ExamSseEvent.builder()
                .eventType(EventType.EXAM_SUBMITTED)
                .attemptId(attemptId)
                .examId(examId)
                .examTitle(examTitle)
                .userId(userId)
                .activeAttemptCount(activeAttemptCount)
                .totalSubmissionsToday(totalSubmissionsToday)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ExamSseEvent attemptStarted(Long attemptId, Long examId,
                                              int activeAttemptCount, int totalSubmissionsToday) {
        return ExamSseEvent.builder()
                .eventType(EventType.ATTEMPT_STARTED)
                .attemptId(attemptId)
                .examId(examId)
                .activeAttemptCount(activeAttemptCount)
                .totalSubmissionsToday(totalSubmissionsToday)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ExamSseEvent attemptEnded(Long attemptId,
                                            int activeAttemptCount, int totalSubmissionsToday) {
        return ExamSseEvent.builder()
                .eventType(EventType.ATTEMPT_ENDED)
                .attemptId(attemptId)
                .activeAttemptCount(activeAttemptCount)
                .totalSubmissionsToday(totalSubmissionsToday)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // Hàm báo kết quả AI của chúng ta
    public static ExamSseEvent aiExtractionResult(Long examId, Long userId, boolean isSuccess, String message) {
        return ExamSseEvent.builder()
                .eventType(isSuccess ? EventType.AI_EXTRACTION_SUCCESS : EventType.AI_EXTRACTION_FAILED)
                .examId(examId)
                .userId(userId)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}