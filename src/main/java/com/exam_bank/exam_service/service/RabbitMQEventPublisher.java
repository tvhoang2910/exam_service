package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.dto.message.ExamSourceUploadedEvent;
import com.exam_bank.exam_service.dto.message.ExamSyncEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.exam_bank.exam_service.dto.ExamSubmittedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RabbitMQEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${exam.events.exchange:exam.events}")
    private String examEventsExchange;

    @Value("${exam.events.routing-key:exam.submitted}")
    private String examEventsRoutingKey;

    public void publishExamSubmitted(ExamSubmittedEvent event) {
        rabbitTemplate.convertAndSend(examEventsExchange, examEventsRoutingKey, event);
        log.info("Published ExamSubmittedEvent: attemptId={}, userId={}", event.getAttemptId(), event.getUserId());
    }
    @Value("${exam.events.file-uploaded-routing-key:exam.source.uploaded}")
    private String fileUploadedRoutingKey;

    public void publishFileUploadedEvent(ExamSourceUploadedEvent event) {
        rabbitTemplate.convertAndSend(examEventsExchange, fileUploadedRoutingKey, event);
        log.info("Published ExamSourceUploadedEvent: examId={}, file={}, byUser={}",
                event.getExamId(), event.getFileObjectName(), event.getUploadedByUserId());
    }

    @Value("${exam.events.sync-routing-key:exam.sync}")
    private String examSyncRoutingKey;

    public void publishExamSyncEvent(ExamSyncEvent event) {
        rabbitTemplate.convertAndSend(examEventsExchange, examSyncRoutingKey, event);
        log.info("Published ExamSyncEvent cho Exam ID: {}", event.getId());
    }
}
