package com.exam_bank.exam_service.feature.upload.repository;

import com.exam_bank.exam_service.feature.upload.entity.ExamUploadRequest;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface ExamUploadRequestRepository extends JpaRepository<ExamUploadRequest, Long> {

    Page<ExamUploadRequest> findByStatus(ExamUploadStatus status, Pageable pageable);

    @Query("""
            select request
            from ExamUploadRequest request
            where request.status = :status
              and request.uploaderId <> :reviewerId
            """)
    Page<ExamUploadRequest> findPendingForReviewer(@Param("status") ExamUploadStatus status,
            @Param("reviewerId") Long reviewerId,
            Pageable pageable);

    Page<ExamUploadRequest> findByUploaderId(Long uploaderId, Pageable pageable);

    Page<ExamUploadRequest> findByUploaderIdAndStatus(Long uploaderId, ExamUploadStatus status, Pageable pageable);

    java.util.Optional<ExamUploadRequest> findByExtractedExamId(Long extractedExamId);

    @Query("""
            select request.extractedExamId
            from ExamUploadRequest request
            where request.extractedExamId in :examIds
              and request.status not in :visibleStatuses
            """)
    Set<Long> findHiddenManagedExamIds(@Param("examIds") Collection<Long> examIds,
            @Param("visibleStatuses") Collection<ExamUploadStatus> visibleStatuses);
}
