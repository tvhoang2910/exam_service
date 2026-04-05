package com.exam_bank.exam_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "exam_audit_logs", indexes = {
    @Index(name = "idx_exam_audit_target", columnList = "target_type, target_id"),
    @Index(name = "idx_exam_audit_actor", columnList = "actor_id"),
    @Index(name = "idx_exam_audit_action", columnList = "action"),
    @Index(name = "idx_exam_audit_created", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
public class ExamAuditLog extends BaseEntity {

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;  // "EXAM", "QUESTION", "REPORT"

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "target_title", length = 500)
    private String targetTitle;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
