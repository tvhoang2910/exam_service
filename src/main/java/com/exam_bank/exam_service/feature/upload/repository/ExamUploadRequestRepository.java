package com.exam_bank.exam_service.feature.upload.repository;

import com.exam_bank.exam_service.feature.upload.entity.ExamUploadRequest;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamUploadRequestRepository extends JpaRepository<ExamUploadRequest, Long> {

    Page<ExamUploadRequest> findByStatus(ExamUploadStatus status, Pageable pageable);

    @Query("""
            select request
            from ExamUploadRequest request
            where request.status = :status
              and exists (
                  select history.id
                  from ExamUploadHistory history
                  where history.uploadRequestId = request.id
                    and history.action = 'SUBMITTED'
              )
            """)
    Page<ExamUploadRequest> findSubmittedByStatus(@Param("status") ExamUploadStatus status, Pageable pageable);

    Page<ExamUploadRequest> findByUploaderId(Long uploaderId, Pageable pageable);

    Page<ExamUploadRequest> findByUploaderIdAndStatus(Long uploaderId, ExamUploadStatus status, Pageable pageable);
}
