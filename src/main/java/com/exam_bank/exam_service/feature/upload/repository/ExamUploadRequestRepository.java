package com.exam_bank.exam_service.feature.upload.repository;

import com.exam_bank.exam_service.feature.upload.entity.ExamUploadRequest;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamUploadRequestRepository extends JpaRepository<ExamUploadRequest, Long> {

    Page<ExamUploadRequest> findByStatus(ExamUploadStatus status, Pageable pageable);

    Page<ExamUploadRequest> findByUploaderId(Long uploaderId, Pageable pageable);

    Page<ExamUploadRequest> findByUploaderIdAndStatus(Long uploaderId, ExamUploadStatus status, Pageable pageable);
}
