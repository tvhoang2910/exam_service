package com.exam_bank.exam_service.listener;

import com.exam_bank.exam_service.dto.message.AiExtractionResultEvent;
import com.exam_bank.exam_service.service.ExtractionResultService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExtractionResultListener unit tests")
class ExtractionResultListenerTest {

    @Mock
    private ExtractionResultService extractionResultService;

    @InjectMocks
    private ExtractionResultListener listener;

    @Test
    void handle_whenEventIsNull_thenReturnWithoutThrow() {
        listener.handle(null);

        verifyNoInteractions(extractionResultService);
    }

    @Test
    void handle_whenServiceThrows_thenRethrowAmqpRejectAndDontRequeueException() {
        AiExtractionResultEvent event = AiExtractionResultEvent.builder()
                .uploadRequestId(12L)
                .examId(34L)
                .successFlag(true)
                .build();

        doThrow(new IllegalStateException("boom"))
                .when(extractionResultService)
                .processExtractionResult(event);

        assertThatThrownBy(() -> listener.handle(event))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessageContaining("uploadRequestId=12");
    }

    @Test
    void handle_whenHappyPath_thenDelegateExactlyOnce() {
        AiExtractionResultEvent event = AiExtractionResultEvent.builder()
                .uploadRequestId(100L)
                .examId(200L)
                .successFlag(true)
                .build();

        listener.handle(event);

        verify(extractionResultService).processExtractionResult(event);
    }

    @Test
    void handle_whenUploadRequestIdIsNull_thenDropEvent() {
        AiExtractionResultEvent event = AiExtractionResultEvent.builder()
                .uploadRequestId(null)
                .examId(200L)
                .successFlag(true)
                .build();

        listener.handle(event);

        verify(extractionResultService, never()).processExtractionResult(event);
    }
}
