package com.exam_bank.exam_service.listener;

import com.exam_bank.exam_service.config.RabbitConfig;
import com.exam_bank.exam_service.dto.ExamSubmittedEvent;
import com.exam_bank.exam_service.service.ExamSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExamEventListener {

    private final ExamSseService examSseService;

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onExamSubmitted(ExamSubmittedEvent event) {
        if (event == null || event.getAttemptId() == null) {
            log.warn("Received null or invalid ExamSubmittedEvent, skipping");
            return;
        }
        log.info("Received ExamSubmittedEvent: attemptId={}, examId={}, userId={}",
                event.getAttemptId(), event.getExamId(), event.getUserId());
        examSseService.onExamSubmitted(
                event.getAttemptId(),
                event.getExamId(),
                event.getExamTitle(),
                event.getUserId());
    }
}
