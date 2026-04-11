package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.dto.message.AdminAlertMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAlertPublisher unit tests")
class AdminAlertPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Captor
    private ArgumentCaptor<AdminAlertMessage> messageCaptor;

    private AdminAlertPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AdminAlertPublisher(rabbitTemplate);
        ReflectionTestUtils.setField(publisher, "notificationExchange", "notification.events");
        ReflectionTestUtils.setField(publisher, "adminAlertRoutingKey", "notification.send.admin.alert");
    }

    @Test
    @DisplayName("publishQuestionReportResolvedAlert sends targeted reporter alert")
    void publishQuestionReportResolvedAlertSendsTargetedReporterAlert() {
        publisher.publishQuestionReportResolvedAlert(
                List.of(10L, 20L),
                99L,
                "De triet hoc",
                123L,
                3,
                "Da sua dap an");

        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("notification.events"),
                org.mockito.ArgumentMatchers.eq("notification.send.admin.alert"),
                messageCaptor.capture());

        AdminAlertMessage message = messageCaptor.getValue();
        assertThat(message.getType()).isEqualTo("QUESTION_REPORT_RESOLVED");
        assertThat(message.getTitle()).isEqualTo("Báo cáo câu hỏi đã được xử lý");
        assertThat(message.getUrl()).isEqualTo("/dashboard");
        assertThat(message.getMetadata())
                .containsEntry("questionId", 123L)
                .containsEntry("examId", 99L)
                .containsEntry("resolvedReportCount", 3)
                .containsEntry("resolutionNote", "Da sua dap an");
        assertThat(message.getMetadata().get("targetUserIds")).isEqualTo(List.of(10L, 20L));
    }

    @Test
    @DisplayName("publishQuestionReportResolvedAlert skips send when reporter list is empty")
    void publishQuestionReportResolvedAlertSkipsWhenReporterListIsEmpty() {
        publisher.publishQuestionReportResolvedAlert(
                List.of(),
                88L,
                "Any exam",
                66L,
                1,
                null);

        verify(rabbitTemplate, never()).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(AdminAlertMessage.class));
    }
}
