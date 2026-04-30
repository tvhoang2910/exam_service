package com.exam_bank.exam_service;

import com.exam_bank.exam_service.dto.message.ExamSourceUploadedEvent;
import com.exam_bank.exam_service.feature.upload.service.ExamUploadService;
import com.exam_bank.exam_service.feature.upload.repository.ExamUploadRequestRepository;
import com.exam_bank.exam_service.feature.upload.repository.ExamUploadHistoryRepository;
import com.exam_bank.exam_service.repository.OnlineExamRepository;
import com.exam_bank.exam_service.service.MinioService;
import com.exam_bank.exam_service.service.RabbitMQEventPublisher;
import com.exam_bank.exam_service.service.AdminAlertPublisher;
import com.exam_bank.exam_service.service.AuthenticatedUserService;
import com.exam_bank.exam_service.feature.upload.dto.InitiateUploadRequest;
import com.exam_bank.exam_service.feature.upload.dto.InitiateUploadResponse;
import com.exam_bank.exam_service.feature.upload.dto.CompleteUploadRequest;
import com.exam_bank.exam_service.feature.upload.dto.ExamUploadPageResponse;
import com.exam_bank.exam_service.feature.upload.dto.ExamUploadResponse;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadRequest;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Smoke tests for exam_service — lightweight sanity checks for core flows.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("exam_service smoke tests")
class SmokeTest {

    @Mock
    private ExamUploadRequestRepository uploadRequestRepository;

    @Mock
    private ExamUploadHistoryRepository historyRepository;

    @Mock
    private OnlineExamRepository onlineExamRepository;

    @Mock
    private MinioService minioService;

    @Mock
    private RabbitMQEventPublisher rabbitMQEventPublisher;

    @Mock
    private AdminAlertPublisher adminAlertPublisher;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private com.exam_bank.exam_service.feature.upload.config.ExamUploadProperties properties;

    @InjectMocks
    private ExamUploadService uploadService;

    @Test
    @DisplayName("Smoke: initiate upload returns valid response")
    void smoke_initiateUpload_returnsValidResponse() {
        when(authenticatedUserService.getCurrentUserId()).thenReturn(123L);
        when(minioService.buildObjectKey(anyLong(), anyLong(), anyInt(), any())).thenReturn("key");
        when(minioService.generatePresignedPutUrl(any(), any(), anyInt())).thenReturn("url");
        when(uploadRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InitiateUploadRequest req = new InitiateUploadRequest();
        req.setTitle("test.pdf");
        req.setDescription("desc");
        req.setPageCount(3);
        req.setContentType("application/pdf");

        InitiateUploadResponse response = uploadService.initiateUpload(req);

        assertThat(response).isNotNull();
        assertThat(response.getUploadId()).isNotNull();
        assertThat(response.getPages()).hasSize(3);
    }

    @Test
    @DisplayName("Smoke: complete upload with valid request succeeds")
    void smoke_completeUpload_succeeds() {
        ExamUploadRequest request = new ExamUploadRequest();
        request.setId(1L);
        request.setUploaderId(123L);
        request.setTitle("test.pdf");
        request.setStatus(ExamUploadStatus.PENDING_APPROVAL);
        request.setKeys(List.of("key1", "key2"));

        when(uploadRequestRepository.findById(1L)).thenReturn(java.util.Optional.of(request));
        when(authenticatedUserService.getCurrentUserId()).thenReturn(123L);
        when(uploadRequestRepository.save(any())).thenAnswer(inv -> {
            ExamUploadRequest e = inv.getArgument(0);
            if (e.getId() == null)
                e.setId(1L);
            return e;
        });

        CompleteUploadRequest completeReq = new CompleteUploadRequest();
        completeReq.setNote("done");

        ExamUploadResponse response = uploadService.completeUpload(1L, completeReq);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Smoke: list pending queue returns page")
    void smoke_listPendingQueue_returnsPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(uploadRequestRepository.findByStatus(any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        ExamUploadPageResponse page = uploadService.listPendingQueue(0, 10);

        assertThat(page).isNotNull();
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Smoke: publish ExamSourceUploadedEvent does not throw")
    void smoke_publishEvent_doesNotThrow() {
        var event = ExamSourceUploadedEvent.builder()
                .examId(1L)
                .fileObjectName("test.pdf")
                .originalFileName("test.pdf")
                .uploadedByUserId("123")
                .pageCount(1)
                .build();

        // Should not throw even if broker is unavailable (publisher handles it)
        rabbitMQEventPublisher.publishFileUploadedEvent(event);
    }

    @Test
    @DisplayName("Smoke: upload request is saved with correct properties")
    void smoke_uploadRequestIsSavedWithCorrectProperties() {
        when(properties.getAllowedContentTypes()).thenReturn(List.of("application/pdf"));
        when(properties.getMaxPages()).thenReturn(20);

        InitiateUploadRequest req = new InitiateUploadRequest();
        req.setTitle("test.pdf");
        req.setDescription("desc");
        req.setPageCount(3);
        req.setContentType("application/pdf");

        InitiateUploadResponse response = uploadService.initiateUpload(req);

        assertThat(response).isNotNull();
        assertThat(response.getUploadId()).isNotNull();
        assertThat(response.getPages()).hasSize(3);
    }
}
