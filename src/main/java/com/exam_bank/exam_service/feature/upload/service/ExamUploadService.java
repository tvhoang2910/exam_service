package com.exam_bank.exam_service.feature.upload.service;

import com.exam_bank.exam_service.dto.message.ExamSourceUploadedEvent;
import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.entity.OnlineExamSource;
import com.exam_bank.exam_service.entity.OnlineExamStatus;
import com.exam_bank.exam_service.feature.upload.config.ExamUploadProperties;
import com.exam_bank.exam_service.feature.upload.dto.CompleteUploadRequest;
import com.exam_bank.exam_service.feature.upload.dto.ExamUploadHistoryResponse;
import com.exam_bank.exam_service.feature.upload.dto.ExamUploadPageResponse;
import com.exam_bank.exam_service.feature.upload.dto.ExamUploadResponse;
import com.exam_bank.exam_service.feature.upload.dto.InitiateUploadRequest;
import com.exam_bank.exam_service.feature.upload.dto.InitiateUploadResponse;
import com.exam_bank.exam_service.feature.upload.dto.InitiateUploadResponse.PresignedPut;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamUploadService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_CONTRIBUTOR = "CONTRIBUTOR";
    private static final String ROLE_USER = "USER";

    private final ExamUploadRequestRepository uploadRequestRepository;
    private final ExamUploadHistoryRepository historyRepository;
    private final OnlineExamRepository onlineExamRepository;
    private final MinioService minioService;
    private final RabbitMQEventPublisher rabbitMQEventPublisher;
    private final AdminAlertPublisher adminAlertPublisher;
    private final AuthenticatedUserService authenticatedUserService;
    private final ExamUploadProperties properties;

    @Transactional
    public InitiateUploadResponse initiateUpload(InitiateUploadRequest req) {
        Long uploaderId = authenticatedUserService.getCurrentUserId();
        String role = currentRole();

        validateContentType(req.getContentType());
        if (req.getPageCount() < 1 || req.getPageCount() > properties.getMaxPages()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "pageCount must be between 1 and " + properties.getMaxPages());
        }

        ExamUploadRequest entity = new ExamUploadRequest();
        entity.setUploaderId(uploaderId);
        entity.setUploaderRole(role);
        entity.setTitle(req.getTitle().trim());
        entity.setDescription(req.getDescription());
        entity.setPageCount(req.getPageCount());
        entity.setContentType(req.getContentType());
        entity.setStatus(ExamUploadStatus.PENDING_APPROVAL);

        // Persist first so we have ID for object key.
        ExamUploadRequest saved = uploadRequestRepository.save(entity);

        List<String> keys = new ArrayList<>(req.getPageCount());
        List<PresignedPut> pages = new ArrayList<>(req.getPageCount());
        for (int i = 1; i <= req.getPageCount(); i++) {
            String key = minioService.buildObjectKey(uploaderId, saved.getId(), i, req.getContentType());
            keys.add(key);
            String url = minioService.generatePresignedPutUrl(key, req.getContentType(),
                    properties.getPresignPutTtlSeconds());
            pages.add(new PresignedPut(i, key, url, properties.getPresignPutTtlSeconds()));
        }
        saved.setKeys(keys);
        uploadRequestRepository.save(saved);

        log.info("Initiated upload {} by user {} ({} pages, contentType={})",
                saved.getId(), uploaderId, req.getPageCount(), req.getContentType());

        return new InitiateUploadResponse(saved.getId(), pages);
    }

    @Transactional
    public ExamUploadResponse completeUpload(Long uploadId, CompleteUploadRequest req) {
        Long userId = authenticatedUserService.getCurrentUserId();
        String role = currentRole();

        ExamUploadRequest request = loadOrThrow(uploadId);
        if (!request.getUploaderId().equals(userId) && !isAdminOrContributor(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to complete this upload");
        }
        if (request.getStatus() != ExamUploadStatus.PENDING_APPROVAL
                && request.getStatus() != ExamUploadStatus.SELF_UPLOADED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Upload is not in a completable state: " + request.getStatus());
        }

        ExamUploadStatus previous = request.getStatus();
        boolean selfUpload = isAdminOrContributor(request.getUploaderRole());
        String action;
        ExamUploadStatus next;
        if (selfUpload) {
            next = ExamUploadStatus.SELF_UPLOADED;
            action = "SELF_UPLOADED";
        } else {
            next = ExamUploadStatus.PENDING_APPROVAL;
            action = "SUBMITTED";
        }
        request.setStatus(next);
        ExamUploadRequest saved = uploadRequestRepository.save(request);
        writeHistory(saved.getId(), action, previous, next, userId, role,
                req == null ? null : req.getNote());

        if (selfUpload) {
            adminAlertPublisher.publishSelfUploadAudit(saved.getId(), saved.getTitle(), saved.getUploaderId(),
                    saved.getUploaderRole());
        } else {
            adminAlertPublisher.publishUploadSubmittedAlert(saved.getId(), saved.getTitle(), saved.getUploaderId(),
                    null);
        }

        log.info("Upload {} completed by user {} (selfUpload={}, newStatus={})",
                saved.getId(), userId, selfUpload, next);

        return toResponse(saved, false);
    }

    @Transactional(readOnly = true)
    public ExamUploadPageResponse listMyUploads(int page, int size, ExamUploadStatus statusFilter) {
        Long userId = authenticatedUserService.getCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ExamUploadRequest> result = (statusFilter != null)
                ? uploadRequestRepository.findByUploaderIdAndStatus(userId, statusFilter, pageable)
                : uploadRequestRepository.findByUploaderId(userId, pageable);
        return ExamUploadPageResponse.from(result.map(e -> toResponse(e, false)));
    }

    @Transactional(readOnly = true)
    public ExamUploadPageResponse listPendingQueue(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.ASC, "createdAt"));
        Page<ExamUploadRequest> result = uploadRequestRepository.findByStatus(ExamUploadStatus.PENDING_APPROVAL,
                pageable);
        return ExamUploadPageResponse.from(result.map(e -> toResponse(e, false)));
    }

    @Transactional(readOnly = true)
    public ExamUploadResponse getDetail(Long uploadId) {
        Long userId = authenticatedUserService.getCurrentUserId();
        String role = currentRole();
        ExamUploadRequest entity = loadOrThrow(uploadId);
        if (!entity.getUploaderId().equals(userId) && !isAdminOrContributor(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view this upload");
        }
        return toResponse(entity, true);
    }

    @Transactional(readOnly = true)
    public List<ExamUploadHistoryResponse> getHistory(Long uploadId) {
        Long userId = authenticatedUserService.getCurrentUserId();
        String role = currentRole();
        ExamUploadRequest entity = loadOrThrow(uploadId);
        if (!entity.getUploaderId().equals(userId) && !isAdminOrContributor(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to view this history");
        }
        return historyRepository.findByUploadRequestIdOrderByCreatedAtDesc(uploadId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @Transactional
    public ExamUploadResponse approve(Long uploadId) {
        Long reviewerId = authenticatedUserService.getCurrentUserId();
        String role = currentRole();
        if (!isAdminOrContributor(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN/CONTRIBUTOR can approve");
        }
        ExamUploadRequest entity = loadOrThrow(uploadId);
        if (entity.getStatus() != ExamUploadStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only PENDING_APPROVAL uploads can be approved. Current=" + entity.getStatus());
        }

        ExamUploadStatus previous = entity.getStatus();
        List<String> keys = entity.getKeys();

        // Create a draft OnlineExam bound to this upload.
        OnlineExam exam = new OnlineExam();
        exam.setTitle(entity.getTitle());
        exam.setDescription(entity.getDescription());
        exam.setSource(OnlineExamSource.AI_EXTRACTED);
        exam.setStatus(OnlineExamStatus.DRAFT);
        exam.setOriginalFileUrl(String.join(",", keys));
        exam.setOriginalFileType(entity.getContentType());
        exam.setTotalQuestions(0);
        OnlineExam savedExam = onlineExamRepository.save(exam);

        entity.setReviewedBy(reviewerId);
        entity.setReviewedAt(Instant.now());
        entity.setExtractedExamId(savedExam.getId());
        entity.setStatus(ExamUploadStatus.APPROVED);
        uploadRequestRepository.save(entity);
        writeHistory(entity.getId(), "APPROVED", previous, ExamUploadStatus.APPROVED, reviewerId, role, null);

        // Transition straight to EXTRACTING.
        entity.setStatus(ExamUploadStatus.EXTRACTING);
        ExamUploadRequest saved = uploadRequestRepository.save(entity);
        writeHistory(saved.getId(), "EXTRACTION_STARTED", ExamUploadStatus.APPROVED, ExamUploadStatus.EXTRACTING,
                reviewerId, role, null);

        // Publish event to extraction service.
        try {
            ExamSourceUploadedEvent event = ExamSourceUploadedEvent.builder()
                    .examId(savedExam.getId())
                    .fileObjectName(keys.isEmpty() ? null : keys.get(0))
                    .originalFileName(entity.getTitle())
                    .uploadedByUserId(String.valueOf(entity.getUploaderId()))
                    .objectKeys(keys)
                    .pageCount(entity.getPageCount())
                    .uploadRequestId(entity.getId())
                    .build();
            rabbitMQEventPublisher.publishFileUploadedEvent(event);
        } catch (AmqpException ex) {
            // Rollback to APPROVED so the upload can be retried, instead of leaving it in EXTRACTING.
            entity.setStatus(ExamUploadStatus.APPROVED);
            uploadRequestRepository.save(entity);
            log.error("Failed to publish ExamSourceUploadedEvent for uploadRequestId={}, rolled back to APPROVED",
                    entity.getId(), ex);
            throw new IllegalStateException(
                    "Failed to publish extraction event for uploadRequestId=" + entity.getId(), ex);
        }

        adminAlertPublisher.publishUploadApprovedAlert(saved.getId(), saved.getTitle(), saved.getUploaderId(),
                reviewerId);

        log.info("Upload {} approved by reviewer {} → examId {} (EXTRACTING)", saved.getId(), reviewerId,
                savedExam.getId());

        return toResponse(saved, false);
    }

    @Transactional
    public ExamUploadResponse reject(Long uploadId, RejectUploadRequest req) {
        Long reviewerId = authenticatedUserService.getCurrentUserId();
        String role = currentRole();
        if (!isAdminOrContributor(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN/CONTRIBUTOR can reject");
        }
        ExamUploadRequest entity = loadOrThrow(uploadId);
        if (entity.getStatus() != ExamUploadStatus.PENDING_APPROVAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only PENDING_APPROVAL uploads can be rejected. Current=" + entity.getStatus());
        }

        ExamUploadStatus previous = entity.getStatus();
        entity.setStatus(ExamUploadStatus.REJECTED);
        entity.setRejectionReason(req.getReason());
        entity.setReviewedBy(reviewerId);
        entity.setReviewedAt(Instant.now());
        ExamUploadRequest saved = uploadRequestRepository.save(entity);
        writeHistory(saved.getId(), "REJECTED", previous, ExamUploadStatus.REJECTED, reviewerId, role,
                req.getReason());

        adminAlertPublisher.publishUploadRejectedAlert(saved.getId(), saved.getTitle(), saved.getUploaderId(),
                reviewerId, req.getReason());

        log.info("Upload {} rejected by reviewer {} (reason length={})", saved.getId(), reviewerId,
                req.getReason() == null ? 0 : req.getReason().length());

        return toResponse(saved, false);
    }

    // ---------- helpers ----------

    private void validateContentType(String contentType) {
        if (contentType == null || !properties.getAllowedContentTypes().contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Content-Type not allowed. Allowed: " + properties.getAllowedContentTypes());
        }
    }

    private ExamUploadRequest loadOrThrow(Long id) {
        return uploadRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Upload not found: " + id));
    }

    private void writeHistory(Long uploadRequestId, String action, ExamUploadStatus previous,
            ExamUploadStatus next, Long actorId, String actorRole, String note) {
        ExamUploadHistory h = new ExamUploadHistory();
        h.setUploadRequestId(uploadRequestId);
        h.setAction(action);
        h.setPreviousStatus(previous);
        h.setNewStatus(next);
        h.setActorId(actorId);
        h.setActorRole(actorRole);
        h.setNote(note);
        historyRepository.save(h);
    }

    private ExamUploadResponse toResponse(ExamUploadRequest e, boolean includeViewUrls) {
        List<String> keys = e.getKeys();
        List<String> viewUrls = null;
        if (includeViewUrls && !keys.isEmpty()) {
            viewUrls = new ArrayList<>(keys.size());
            for (String key : keys) {
                viewUrls.add(minioService.generatePresignedGetUrl(key, properties.getPresignGetTtlSeconds()));
            }
        }
        return ExamUploadResponse.builder()
                .id(e.getId())
                .uploaderId(e.getUploaderId())
                .uploaderRole(e.getUploaderRole())
                .title(e.getTitle())
                .description(e.getDescription())
                .pageCount(e.getPageCount())
                .contentType(e.getContentType())
                .status(e.getStatus())
                .rejectionReason(e.getRejectionReason())
                .reviewedBy(e.getReviewedBy())
                .reviewedAt(e.getReviewedAt())
                .extractedExamId(e.getExtractedExamId())
                .extractionError(e.getExtractionError())
                .createdAt(e.getCreatedAt())
                .modifiedAt(e.getModifiedAt())
                .objectKeys(keys)
                .viewUrls(viewUrls)
                .build();
    }

    private ExamUploadHistoryResponse toHistoryResponse(ExamUploadHistory h) {
        return ExamUploadHistoryResponse.builder()
                .id(h.getId())
                .action(h.getAction())
                .previousStatus(h.getPreviousStatus())
                .newStatus(h.getNewStatus())
                .actorId(h.getActorId())
                .actorRole(h.getActorRole())
                .note(h.getNote())
                .createdAt(h.getCreatedAt())
                .build();
    }

    private String currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return ROLE_USER;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String a = ga.getAuthority();
            if (a == null) {
                continue;
            }
            if (a.equals("ROLE_ADMIN")) {
                return ROLE_ADMIN;
            }
            if (a.equals("ROLE_CONTRIBUTOR")) {
                return ROLE_CONTRIBUTOR;
            }
            if (a.equals("ROLE_USER")) {
                return ROLE_USER;
            }
        }
        return ROLE_USER;
    }

    private boolean isAdminOrContributor(String role) {
        return ROLE_ADMIN.equals(role) || ROLE_CONTRIBUTOR.equals(role);
    }
}
