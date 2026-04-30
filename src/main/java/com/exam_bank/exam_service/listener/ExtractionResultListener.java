package com.exam_bank.exam_service.listener;

import com.exam_bank.exam_service.dto.message.AiExtractionResultEvent;
import com.exam_bank.exam_service.service.ExtractionResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExtractionResultListener {

    private final ExtractionResultService extractionResultService;

    @RabbitListener(queues = "${exam.extraction.result.queue}")
    public void handle(AiExtractionResultEvent event) {
        if (event == null) {
            log.warn("Received null AiExtractionResultEvent, dropping");
            return;
        }
        if (event.getUploadRequestId() == null) {
            log.warn("Received AiExtractionResultEvent with null uploadRequestId examId={}, dropping",
                    event.getExamId());
            return;
        }

        log.info("Received AI extraction result: uploadRequestId={} examId={} success={}",
                event.getUploadRequestId(), event.getExamId(), event.getSuccessFlag());

        try {
            extractionResultService.processExtractionResult(event);
        } catch (Exception ex) {
            log.error("Failed to process AI extraction result for uploadRequestId={} examId={}: {}",
                    event.getUploadRequestId(), event.getExamId(), ex.getMessage(), ex);
            throw new AmqpRejectAndDontRequeueException(
                    "Failed to process extraction result for uploadRequestId=" + event.getUploadRequestId(), ex);
        }
    }
}
