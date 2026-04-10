package com.exam_bank.exam_service.contract;

import com.exam_bank.exam_service.dto.ExamSubmittedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExamSubmitted Event Contract Tests")
class ExamSubmittedEventContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("producer payload contains fields required by study_service consumer")
    void producerPayloadContainsRequiredConsumerFields() throws Exception {
        ExamSubmittedEvent event = sampleEvent();

        String json = objectMapper.writeValueAsString(event);
        JsonNode root = objectMapper.readTree(json);

        assertThat(root.path("attemptId").asLong()).isEqualTo(9001L);
        assertThat(root.path("userId").asLong()).isEqualTo(42L);
        assertThat(root.path("examId").asLong()).isEqualTo(88L);
        assertThat(root.path("examTitle").asText()).isEqualTo("Contract Exam");
        assertThat(root.path("submittedAt").asText()).isEqualTo("2026-04-10T07:00:00Z");
        assertThat(root.path("scoreRaw").asDouble()).isEqualTo(7.0);
        assertThat(root.path("scoreMax").asDouble()).isEqualTo(10.0);
        assertThat(root.path("scorePercent").asDouble()).isEqualTo(70.0);
        assertThat(root.path("durationSeconds").asLong()).isEqualTo(180L);

        JsonNode questions = root.path("questions");
        assertThat(questions.isArray()).isTrue();
        assertThat(questions).hasSize(2);

        JsonNode firstQuestion = questions.get(0);
        assertThat(firstQuestion.path("questionId").asLong()).isEqualTo(7001L);
        assertThat(firstQuestion.path("isCorrect").asBoolean()).isTrue();
        assertThat(firstQuestion.path("earnedScore").asDouble()).isEqualTo(1.0);
        assertThat(firstQuestion.path("maxScore").asDouble()).isEqualTo(1.0);
        assertThat(firstQuestion.path("selectedOptionIds").asText()).isEqualTo("11");
        assertThat(firstQuestion.path("correctOptionIds").asText()).isEqualTo("11");
        assertThat(firstQuestion.path("responseTimeMs").asLong()).isEqualTo(12000L);
        assertThat(firstQuestion.path("answerChangeCount").asInt()).isEqualTo(0);
        assertThat(firstQuestion.path("difficulty").asDouble()).isEqualTo(1.0);
        assertThat(firstQuestion.path("tagIds").asText()).isEqualTo("7,8");

        JsonNode examTags = root.path("examTags");
        assertThat(examTags.isArray()).isTrue();
        assertThat(examTags).hasSize(1);
        assertThat(examTags.get(0).path("tagId").asLong()).isEqualTo(7L);
        assertThat(examTags.get(0).path("tagName").asText()).isEqualTo("Algebra");
    }

    @Test
    @DisplayName("producer payload can be deserialized by a consumer-compatible model")
    void producerPayloadCanBeDeserializedByConsumerCompatibleModel() throws Exception {
        String json = objectMapper.writeValueAsString(sampleEvent());

        StudyCompatibleEvent mapped = objectMapper.readValue(json, StudyCompatibleEvent.class);

        assertThat(mapped.attemptId).isEqualTo(9001L);
        assertThat(mapped.userId).isEqualTo(42L);
        assertThat(mapped.questions).hasSize(2);
        assertThat(mapped.questions.get(0).questionId).isEqualTo(7001L);
        assertThat(mapped.questions.get(1).isCorrect).isFalse();
        assertThat(mapped.examTags).hasSize(1);
        assertThat(mapped.examTags.get(0).tagName).isEqualTo("Algebra");
    }

    private ExamSubmittedEvent sampleEvent() {
        ExamSubmittedEvent event = new ExamSubmittedEvent();
        event.setAttemptId(9001L);
        event.setUserId(42L);
        event.setExamId(88L);
        event.setExamTitle("Contract Exam");
        event.setSubmittedAt(Instant.parse("2026-04-10T07:00:00Z"));
        event.setScoreRaw(7.0);
        event.setScoreMax(10.0);
        event.setScorePercent(70.0);
        event.setDurationSeconds(180L);

        ExamSubmittedEvent.QuestionAnswered q1 = new ExamSubmittedEvent.QuestionAnswered();
        q1.setQuestionId(7001L);
        q1.setIsCorrect(true);
        q1.setEarnedScore(1.0);
        q1.setMaxScore(1.0);
        q1.setSelectedOptionIds("11");
        q1.setCorrectOptionIds("11");
        q1.setResponseTimeMs(12000L);
        q1.setAnswerChangeCount(0);
        q1.setDifficulty(1.0);
        q1.setTagIds("7,8");

        ExamSubmittedEvent.QuestionAnswered q2 = new ExamSubmittedEvent.QuestionAnswered();
        q2.setQuestionId(7002L);
        q2.setIsCorrect(false);
        q2.setEarnedScore(0.0);
        q2.setMaxScore(1.0);
        q2.setSelectedOptionIds("12");
        q2.setCorrectOptionIds("13");
        q2.setResponseTimeMs(31000L);
        q2.setAnswerChangeCount(2);
        q2.setDifficulty(1.0);
        q2.setTagIds("7,8");

        ExamSubmittedEvent.TagInfo tagInfo = new ExamSubmittedEvent.TagInfo();
        tagInfo.setTagId(7L);
        tagInfo.setTagName("Algebra");

        event.setQuestions(List.of(q1, q2));
        event.setExamTags(List.of(tagInfo));
        return event;
    }

    private static class StudyCompatibleEvent {
        public Long attemptId;
        public Long userId;
        public Long examId;
        public String examTitle;
        public Instant submittedAt;
        public Double scoreRaw;
        public Double scoreMax;
        public Double scorePercent;
        public Long durationSeconds;
        public List<StudyCompatibleQuestion> questions;
        public List<StudyCompatibleTag> examTags;
    }

    private static class StudyCompatibleQuestion {
        public Long questionId;
        public Boolean isCorrect;
        public Double earnedScore;
        public Double maxScore;
        public String selectedOptionIds;
        public String correctOptionIds;
        public Long responseTimeMs;
        public Integer answerChangeCount;
        public Double difficulty;
        public String tagIds;
    }

    private static class StudyCompatibleTag {
        public Long tagId;
        public String tagName;
    }
}
