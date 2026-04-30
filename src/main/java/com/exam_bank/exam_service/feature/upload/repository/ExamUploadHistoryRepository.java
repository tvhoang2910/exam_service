package com.exam_bank.exam_service.feature.upload.repository;

import com.exam_bank.exam_service.feature.upload.entity.ExamUploadHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamUploadHistoryRepository extends JpaRepository<ExamUploadHistory, Long> {

    List<ExamUploadHistory> findByUploadRequestIdOrderByCreatedAtDesc(Long uploadRequestId);
}
