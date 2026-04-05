package com.exam_bank.exam_service.repository;

import com.exam_bank.exam_service.entity.ExamAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamAuditLogRepository extends JpaRepository<ExamAuditLog, Long> {
    Page<ExamAuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId, Pageable pageable);
    Page<ExamAuditLog> findByActorIdOrderByCreatedAtDesc(Long actorId, Pageable pageable);

    @Query("select log from ExamAuditLog log where log.targetType = :targetType and log.targetId = :targetId order by log.createdAt asc")
    List<ExamAuditLog> findHistoryByTarget(String targetType, Long targetId);
}
