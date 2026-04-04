package com.exam_bank.exam_service.dto.internal;

public class ExamSseEvent {

    public enum EventType {
        EXAM_SUBMITTED,
        ATTEMPT_STARTED,
        ATTEMPT_ENDED,
        SNAPSHOT
    }

    private EventType eventType;
    private Long attemptId;
    private Long examId;
    private String examTitle;
    private Long userId;
    private int activeAttemptCount;
    private int totalSubmissionsToday;
    private long timestamp;

    public ExamSseEvent() {}

    private ExamSseEvent(EventType eventType, Long attemptId, Long examId,
                         String examTitle, Long userId,
                         int activeAttemptCount, int totalSubmissionsToday, long timestamp) {
        this.eventType = eventType;
        this.attemptId = attemptId;
        this.examId = examId;
        this.examTitle = examTitle;
        this.userId = userId;
        this.activeAttemptCount = activeAttemptCount;
        this.totalSubmissionsToday = totalSubmissionsToday;
        this.timestamp = timestamp;
    }

    public static ExamSseEvent snapshot(int activeAttemptCount, int totalSubmissionsToday) {
        return new ExamSseEvent(EventType.SNAPSHOT, null, null, null, null,
                activeAttemptCount, totalSubmissionsToday, System.currentTimeMillis());
    }

    public static ExamSseEvent submitted(Long attemptId, Long examId, String examTitle,
                                         Long userId, int activeAttemptCount, int totalSubmissionsToday) {
        return new ExamSseEvent(EventType.EXAM_SUBMITTED, attemptId, examId, examTitle, userId,
                activeAttemptCount, totalSubmissionsToday, System.currentTimeMillis());
    }

    public static ExamSseEvent attemptStarted(Long attemptId, Long examId,
                                              int activeAttemptCount, int totalSubmissionsToday) {
        return new ExamSseEvent(EventType.ATTEMPT_STARTED, attemptId, examId, null, null,
                activeAttemptCount, totalSubmissionsToday, System.currentTimeMillis());
    }

    public static ExamSseEvent attemptEnded(Long attemptId,
                                            int activeAttemptCount, int totalSubmissionsToday) {
        return new ExamSseEvent(EventType.ATTEMPT_ENDED, attemptId, null, null, null,
                activeAttemptCount, totalSubmissionsToday, System.currentTimeMillis());
    }

    public EventType getEventType() { return eventType; }
    public Long getAttemptId() { return attemptId; }
    public Long getExamId() { return examId; }
    public String getExamTitle() { return examTitle; }
    public Long getUserId() { return userId; }
    public int getActiveAttemptCount() { return activeAttemptCount; }
    public int getTotalSubmissionsToday() { return totalSubmissionsToday; }
    public long getTimestamp() { return timestamp; }
}
