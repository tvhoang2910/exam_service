package com.exam_bank.exam_service.feature.upload.service;

import com.exam_bank.exam_service.dto.message.ExamSourceUploadedEvent;
import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.entity.OnlineExamSource;
import com.exam_bank.exam_service.entity.OnlineExamStatus;
import com.exam_bank.exam_service.feature.upload.config.ExamUploadProperties;
import com.exam_bank.exam_service.feature.upload.dto.CompleteUploadRequest;
import com.exam_bank.exam_service.feature.upload.dto.ExamUploadPageResponse;
import com.exam_bank.exam_service.feature.upload.dto.ExamUploadResponse;
import com.exam_bank.exam_service.feature.upload.dto.InitiateUploadRequest;
import com.exam_bank.exam_service.feature.upload.dto.InitiateUploadResponse;
import com.exam_bank.exam_service.feature.upload.dto.RejectUploadRequest;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadHistory;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadRequest;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadStatus;
import com.exam_bank.exam_service.feature.upload.repository.ExamUploadHistoryRepository;
import com.exam_bank.exam_service.feature.upload.repository.ExamUploadRequestRepository;
import com.exam_bank.exam_service.repository.OnlineExamRepository;
import com.exam_bank.exam_service.service.AdminAlertPublisher;
import com.exam_bank.exam_service.service.AuthenticatedUserService;
import com.exam_bank.exam_service.service.MinioService;
import com.exam_bank.exam_service.service.RabbitMQEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExamUploadService unit tests")
class ExamUploadServiceTest {

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

    private ExamUploadProperties properties;

    private ExamUploadService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        properties = new ExamUploadProperties();
        service = new ExamUploadService(
                uploadRequestRepository,
                historyRepository,
                onlineExamRepository,
                minioService,
                rabbitMQEventPublisher,
                adminAlertPublisher,
                authenticatedUserService,
                properties);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String role) {
        Authentication auth = new TestingAuthenticationToken("u", "p", "ROLE_" + role);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private ExamUploadRequest buildUpload(Long id, Long uploaderId, String uploaderRole,
            ExamUploadStatus status) {
        ExamUploadRequest entity = new ExamUploadRequest();
        entity.setId(id);
        entity.setUploaderId(uploaderId);
        entity.setUploaderRole(uploaderRole);
        entity.setTitle("Mock title");
        entity.setDescription("desc");
        entity.setPageCount(2);
        entity.setContentType("image/jpeg");
        entity.setStatus(status);
        entity.setKeys(List.of("k1", "k2"));
        return entity;
    }

    // -------------------- initiateUpload --------------------

    @Test
    @DisplayName("initiateUpload as USER persists PENDING_APPROVAL and returns presigned urls")
    void initiateUpload_userRole_persistsPendingAndReturnsPresignedUrls() {
        authenticateAs("USER");
        when(authenticatedUserService.getCurrentUserId()).thenReturn(42L);

        InitiateUploadRequest req = new InitiateUploadRequest("My Exam", "desc", 3, "image/jpeg");

        ExamUploadRequest firstSaved = new ExamUploadRequest();
        firstSaved.setId(100L);
        firstSaved.setUploaderId(42L);
        firstSaved.setUploaderRole("USER");
        firstSaved.setTitle("My Exam");
        firstSaved.setPageCount(3);
        firstSaved.setContentType("image/jpeg");
        firstSaved.setStatus(ExamUploadStatus.PENDING_APPROVAL);

        when(uploadRequestRepository.save(any(ExamUploadRequest.class))).thenReturn(firstSaved);
        when(minioService.buildObjectKey(eq(42L), eq(100L), anyInt(), eq("image/jpeg")))
                .thenAnswer(inv -> "uploads/42/100/page-" + inv.getArgument(2) + ".jpg");
        when(minioService.generatePresignedPutUrl(anyString(), eq("image/jpeg"), anyInt()))
                .thenAnswer(inv -> "https://minio.local/" + inv.getArgument(0));

        InitiateUploadResponse resp = service.initiateUpload(req);

        assertThat(resp.getUploadId()).isEqualTo(100L);
        assertThat(resp.getPages()).hasSize(3);
        assertThat(resp.getPages().get(0).getIndex()).isEqualTo(1);
        assertThat(resp.getPages().get(2).getIndex()).isEqualTo(3);
        assertThat(resp.getPages().get(0).getObjectKey()).isEqualTo("uploads/42/100/page-1.jpg");
        assertThat(resp.getPages().get(0).getUrl()).startsWith("https://minio.local/");
        assertThat(resp.getPages().get(0).getExpiresInSeconds()).isEqualTo(properties.getPresignPutTtlSeconds());

        ArgumentCaptor<ExamUploadRequest> captor = ArgumentCaptor.forClass(ExamUploadRequest.class);
        verify(uploadRequestRepository, times(2)).save(captor.capture());
        ExamUploadRequest firstCall = captor.getAllValues().get(0);
        assertThat(firstCall.getStatus()).isEqualTo(ExamUploadStatus.PENDING_APPROVAL);
        assertThat(firstCall.getUploaderId()).isEqualTo(42L);
        assertThat(firstCall.getUploaderRole()).isEqualTo("USER");

        verifyNoInteractions(adminAlertPublisher, rabbitMQEventPublisher, historyRepository);
    }

    @Test
    @DisplayName("initiateUpload rejects unsupported content type with 400")
    void initiateUpload_rejectsUnsupportedContentType_throws400() {
        authenticateAs("USER");
        when(authenticatedUserService.getCurrentUserId()).thenReturn(42L);

        InitiateUploadRequest req = new InitiateUploadRequest("t", null, 1, "application/zip");

        assertThatThrownBy(() -> service.initiateUpload(req))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(uploadRequestRepository, minioService);
    }

    @Test
    @DisplayName("initiateUpload rejects page count out of range (0 and > maxPages)")
    void initiateUpload_rejectsPageCountOutOfRange() {
        authenticateAs("USER");
        when(authenticatedUserService.getCurrentUserId()).thenReturn(42L);

        InitiateUploadRequest tooFew = new InitiateUploadRequest("t", null, 0, "image/jpeg");
        assertThatThrownBy(() -> service.initiateUpload(tooFew))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        InitiateUploadRequest tooMany = new InitiateUploadRequest("t", null,
                properties.getMaxPages() + 1, "image/jpeg");
        assertThatThrownBy(() -> service.initiateUpload(tooMany))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(uploadRequestRepository, minioService);
    }

    // -------------------- completeUpload --------------------

    @Test
    @DisplayName("completeUpload by owner USER records SUBMITTED history and notifies admins")
    void completeUpload_byOwnerUser_writesSubmittedHistory_notifiesAdmins() {
        authenticateAs("USER");
        when(authenticatedUserService.getCurrentUserId()).thenReturn(42L);
        ExamUploadRequest existing = buildUpload(100L, 42L, "USER", ExamUploadStatus.PENDING_APPROVAL);
        when(uploadRequestRepository.findById(100L)).thenReturn(java.util.Optional.of(existing));
        when(uploadRequestRepository.save(any(ExamUploadRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ExamUploadResponse resp = service.completeUpload(100L, new CompleteUploadRequest("ready"));

        assertThat(resp.getStatus()).isEqualTo(ExamUploadStatus.PENDING_APPROVAL);

        ArgumentCaptor<ExamUploadHistory> historyCaptor = ArgumentCaptor.forClass(ExamUploadHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        ExamUploadHistory history = historyCaptor.getValue();
        assertThat(history.getAction()).isEqualTo("SUBMITTED");
        assertThat(history.getNewStatus()).isEqualTo(ExamUploadStatus.PENDING_APPROVAL);
        assertThat(history.getActorId()).isEqualTo(42L);
        assertThat(history.getActorRole()).isEqualTo("USER");
        assertThat(history.getNote()).isEqualTo("ready");

        verify(adminAlertPublisher).publishUploadSubmittedAlert(eq(100L), eq("Mock title"), eq(42L), any());
        verify(adminAlertPublisher, never()).publishSelfUploadAudit(anyLong(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("completeUpload by ADMIN self-upload switches to SELF_UPLOADED and publishes audit")
    void completeUpload_byAdminSelf_setsSelfUploadedStatus_auditNotification() {
        authenticateAs("ADMIN");
        when(authenticatedUserService.getCurrentUserId()).thenReturn(7L);
        ExamUploadRequest existing = buildUpload(200L, 7L, "ADMIN", ExamUploadStatus.PENDING_APPROVAL);
        when(uploadRequestRepository.findById(200L)).thenReturn(java.util.Optional.of(existing));
        when(uploadRequestRepository.save(any(ExamUploadRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ExamUploadResponse resp = service.completeUpload(200L, new CompleteUploadRequest(null));

        assertThat(resp.getStatus()).isEqualTo(ExamUploadStatus.SELF_UPLOADED);

        ArgumentCaptor<ExamUploadHistory> historyCaptor = ArgumentCaptor.forClass(ExamUploadHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getAction()).isEqualTo("SELF_UPLOADED");
        assertThat(historyCaptor.getValue().getNewStatus()).isEqualTo(ExamUploadStatus.SELF_UPLOADED);

        verify(adminAlertPublisher).publishSelfUploadAudit(eq(200L), eq("Mock title"), eq(7L), eq("ADMIN"));
        verify(adminAlertPublisher, never()).publishUploadSubmittedAlert(anyLong(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("completeUpload by non-owner non-admin throws 403")
    void completeUpload_byNonOwnerNonAdmin_throws403() {
        authenticateAs("USER");
        when(authenticatedUserService.getCurrentUserId()).thenReturn(99L);
        ExamUploadRequest existing = buildUpload(100L, 42L, "USER", ExamUploadStatus.PENDING_APPROVAL);
        when(uploadRequestRepository.findById(100L)).thenReturn(java.util.Optional.of(existing));

        assertThatThrownBy(() -> service.completeUpload(100L, new CompleteUploadRequest(null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(historyRepository, adminAlertPublisher);
    }

    // -------------------- approve --------------------

    @Test
    @DisplayName("approve transitions to EXTRACTING, creates draft exam, publishes event, notifies owner")
    void approve_transitionsToExtracting_createsDraftExam_publishesEvent_notifiesOwner() {
        authenticateAs("ADMIN");
        when(authenticatedUserService.getCurrentUserId()).thenReturn(555L);

        ExamUploadRequest existing = buildUpload(300L, 42L, "USER", ExamUploadStatus.PENDING_APPROVAL);
        existing.setPageCount(2);
        when(uploadRequestRepository.findById(300L)).thenReturn(java.util.Optional.of(existing));
        when(uploadRequestRepository.save(any(ExamUploadRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        OnlineExam savedExam = new OnlineExam();
        savedExam.setId(9000L);
        when(onlineExamRepository.save(any(OnlineExam.class))).thenReturn(savedExam);

        ExamUploadResponse resp = service.approve(300L);

        assertThat(resp.getStatus()).isEqualTo(ExamUploadStatus.EXTRACTING);
        assertThat(resp.getExtractedExamId()).isEqualTo(9000L);

        ArgumentCaptor<OnlineExam> examCaptor = ArgumentCaptor.forClass(OnlineExam.class);
        verify(onlineExamRepository).save(examCaptor.capture());
        OnlineExam savedArg = examCaptor.getValue();
        assertThat(savedArg.getSource()).isEqualTo(OnlineExamSource.AI_EXTRACTED);
        assertThat(savedArg.getStatus()).isEqualTo(OnlineExamStatus.DRAFT);
        assertThat(savedArg.getTitle()).isEqualTo("Mock title");
        assertThat(savedArg.getOriginalFileUrl()).isEqualTo("k1,k2");
        assertThat(savedArg.getOriginalFileType()).isEqualTo("image/jpeg");

        ArgumentCaptor<ExamSourceUploadedEvent> eventCaptor = ArgumentCaptor.forClass(ExamSourceUploadedEvent.class);
        verify(rabbitMQEventPublisher).publishFileUploadedEvent(eventCaptor.capture());
        ExamSourceUploadedEvent event = eventCaptor.getValue();
        assertThat(event.getUploadRequestId()).isEqualTo(300L);
        assertThat(event.getPageCount()).isEqualTo(2);
        assertThat(event.getObjectKeys()).containsExactly("k1", "k2");
        assertThat(event.getExamId()).isEqualTo(9000L);
        assertThat(event.getUploadedByUserId()).isEqualTo("42");

        ArgumentCaptor<ExamUploadHistory> historyCaptor = ArgumentCaptor.forClass(ExamUploadHistory.class);
        verify(historyRepository, times(2)).save(historyCaptor.capture());
        List<ExamUploadHistory> rows = historyCaptor.getAllValues();
        assertThat(rows.get(0).getAction()).isEqualTo("APPROVED");
        assertThat(rows.get(0).getNewStatus()).isEqualTo(ExamUploadStatus.APPROVED);
        assertThat(rows.get(1).getAction()).isEqualTo("EXTRACTION_STARTED");
        assertThat(rows.get(1).getNewStatus()).isEqualTo(ExamUploadStatus.EXTRACTING);

        verify(adminAlertPublisher).publishUploadApprovedAlert(eq(300L), eq("Mock title"), eq(42L), eq(555L));
    }

    @Test
    @DisplayName("approve from a non-pending state throws 409")
    void approve_fromNonPending_throws409() {
        authenticateAs("ADMIN");
        when(authenticatedUserService.getCurrentUserId()).thenReturn(555L);
        ExamUploadRequest existing = buildUpload(300L, 42L, "USER", ExamUploadStatus.APPROVED);
        when(uploadRequestRepository.findById(300L)).thenReturn(java.util.Optional.of(existing));

        assertThatThrownBy(() -> service.approve(300L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(onlineExamRepository, rabbitMQEventPublisher);
    }

    @Test
    @DisplayName("approve rolls back to APPROVED and throws IllegalStateException when publish fails")
    void approve_whenPublishThrowsAmqpException_thenRollbackStatusAndThrow() {
        authenticateAs("ADMIN");
        when(authenticatedUserService.getCurrentUserId()).thenReturn(555L);

        ExamUploadRequest existing = buildUpload(301L, 42L, "USER", ExamUploadStatus.PENDING_APPROVAL);
        existing.setPageCount(2);
        when(uploadRequestRepository.findById(301L)).thenReturn(java.util.Optional.of(existing));
        when(uploadRequestRepository.save(any(ExamUploadRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        OnlineExam savedExam = new OnlineExam();
        savedExam.setId(9001L);
        when(onlineExamRepository.save(any(OnlineExam.class))).thenReturn(savedExam);

        doThrow(new AmqpException("Broker unavailable") {
        }).when(rabbitMQEventPublisher).publishFileUploadedEvent(any(ExamSourceUploadedEvent.class));

        assertThatThrownBy(() -> service.approve(301L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("uploadRequestId=301")
                .hasCauseInstanceOf(AmqpException.class);

        ArgumentCaptor<ExamUploadRequest> uploadCaptor = ArgumentCaptor.forClass(ExamUploadRequest.class);
        verify(uploadRequestRepository, atLeast(3)).save(uploadCaptor.capture());
        List<ExamUploadRequest> savedStates = uploadCaptor.getAllValues();
        assertThat(savedStates.get(savedStates.size() - 1).getStatus()).isEqualTo(ExamUploadStatus.APPROVED);

        ArgumentCaptor<ExamUploadHistory> historyCaptor = ArgumentCaptor.forClass(ExamUploadHistory.class);
        verify(historyRepository, times(2)).save(historyCaptor.capture());
        assertThat(historyCaptor.getAllValues().get(1).getAction()).isEqualTo("EXTRACTION_STARTED");
        assertThat(historyCaptor.getAllValues().get(1).getNewStatus()).isEqualTo(ExamUploadStatus.EXTRACTING);

        verify(adminAlertPublisher, never()).publishUploadApprovedAlert(anyLong(), anyString(), anyLong(), anyLong());
    }

    // -------------------- reject --------------------

    @Test
    @DisplayName("reject stores reason, writes REJECTED history, and notifies owner")
    void reject_storesReason_writesHistory_notifiesOwner() {
        authenticateAs("ADMIN");
        when(authenticatedUserService.getCurrentUserId()).thenReturn(555L);
        ExamUploadRequest existing = buildUpload(400L, 42L, "USER", ExamUploadStatus.PENDING_APPROVAL);
        when(uploadRequestRepository.findById(400L)).thenReturn(java.util.Optional.of(existing));
        when(uploadRequestRepository.save(any(ExamUploadRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        RejectUploadRequest req = new RejectUploadRequest("Blurry scan");
        ExamUploadResponse resp = service.reject(400L, req);

        assertThat(resp.getStatus()).isEqualTo(ExamUploadStatus.REJECTED);
        assertThat(resp.getRejectionReason()).isEqualTo("Blurry scan");

        ArgumentCaptor<ExamUploadHistory> historyCaptor = ArgumentCaptor.forClass(ExamUploadHistory.class);
        verify(historyRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getAction()).isEqualTo("REJECTED");
        assertThat(historyCaptor.getValue().getNewStatus()).isEqualTo(ExamUploadStatus.REJECTED);
        assertThat(historyCaptor.getValue().getNote()).isEqualTo("Blurry scan");

        verify(adminAlertPublisher).publishUploadRejectedAlert(eq(400L), eq("Mock title"), eq(42L),
                eq(555L), eq("Blurry scan"));
        verifyNoInteractions(onlineExamRepository, rabbitMQEventPublisher);
    }

    @Test
    @DisplayName("reject from a non-pending state throws 409")
    void reject_fromNonPending_throws409() {
        authenticateAs("ADMIN");
        when(authenticatedUserService.getCurrentUserId()).thenReturn(555L);
        ExamUploadRequest existing = buildUpload(400L, 42L, "USER", ExamUploadStatus.REJECTED);
        when(uploadRequestRepository.findById(400L)).thenReturn(java.util.Optional.of(existing));

        assertThatThrownBy(() -> service.reject(400L, new RejectUploadRequest("x")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(adminAlertPublisher);
    }

    // -------------------- list queries --------------------

    @Test
    @DisplayName("listMyUploads filters by uploader and optional status")
    void listMyUploads_filtersByUploaderAndOptionalStatus() {
        when(authenticatedUserService.getCurrentUserId()).thenReturn(42L);
        ExamUploadRequest e = buildUpload(10L, 42L, "USER", ExamUploadStatus.PENDING_APPROVAL);
        Page<ExamUploadRequest> page = new PageImpl<>(List.of(e));

        when(uploadRequestRepository.findByUploaderId(eq(42L), any(Pageable.class))).thenReturn(page);

        ExamUploadPageResponse resp1 = service.listMyUploads(0, 10, null);
        assertThat(resp1.getContent()).hasSize(1);
        assertThat(resp1.getContent().get(0).getId()).isEqualTo(10L);
        verify(uploadRequestRepository).findByUploaderId(eq(42L), any(Pageable.class));

        when(uploadRequestRepository.findByUploaderIdAndStatus(eq(42L),
                eq(ExamUploadStatus.PENDING_APPROVAL), any(Pageable.class))).thenReturn(page);

        ExamUploadPageResponse resp2 = service.listMyUploads(0, 10, ExamUploadStatus.PENDING_APPROVAL);
        assertThat(resp2.getContent()).hasSize(1);
        verify(uploadRequestRepository).findByUploaderIdAndStatus(eq(42L),
                eq(ExamUploadStatus.PENDING_APPROVAL), any(Pageable.class));
    }

    @Test
    @DisplayName("listPendingQueue returns only PENDING_APPROVAL entries")
    void listPendingQueue_returnsOnlyPendingStatus() {
        ExamUploadRequest e = buildUpload(11L, 42L, "USER", ExamUploadStatus.PENDING_APPROVAL);
        Page<ExamUploadRequest> page = new PageImpl<>(List.of(e));
        when(uploadRequestRepository.findByStatus(eq(ExamUploadStatus.PENDING_APPROVAL), any(Pageable.class)))
                .thenReturn(page);

        ExamUploadPageResponse resp = service.listPendingQueue(0, 20);

        assertThat(resp.getContent()).hasSize(1);
        assertThat(resp.getContent().get(0).getStatus()).isEqualTo(ExamUploadStatus.PENDING_APPROVAL);
        verify(uploadRequestRepository).findByStatus(eq(ExamUploadStatus.PENDING_APPROVAL), any(Pageable.class));
        verifyNoInteractions(authenticatedUserService);
    }
}
