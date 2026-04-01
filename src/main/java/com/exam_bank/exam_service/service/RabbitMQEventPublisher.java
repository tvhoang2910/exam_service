package com.exam_bank.exam_service.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.exam_bank.exam_service.config.RabbitConfig;
import com.exam_bank.exam_service.dto.ExamSubmittedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RabbitMQEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishExamSubmitted(ExamSubmittedEvent event) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, event);
        log.info("Published ExamSubmittedEvent: attemptId={}, userId={}", event.getAttemptId(), event.getUserId());
    }
}
