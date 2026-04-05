package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.entity.ExamAuditLog;
import com.exam_bank.exam_service.repository.ExamAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamAuditService {

    private final ExamAuditLogRepository auditLogRepository;

    // Action constants
    public static final String ACTION_EXAM_CREATED = "EXAM_CREATED";
    public static final String ACTION_EXAM_UPDATED = "EXAM_UPDATED";
    public static final String ACTION_EXAM_DELETED = "EXAM_DELETED";
    public static final String ACTION_EXAM_ARCHIVED = "EXAM_ARCHIVED";
    public static final String ACTION_EXAM_STATUS_CHANGED = "EXAM_STATUS_CHANGED";
    public static final String ACTION_QUESTION_HIDDEN = "QUESTION_HIDDEN";
    public static final String ACTION_QUESTION_UNHIDDEN = "QUESTION_UNHIDDEN";
    public static final String ACTION_QUESTION_AUTO_HIDDEN = "QUESTION_AUTO_HIDDEN";
    public static final String ACTION_REPORT_RESOLVED = "REPORT_RESOLVED";
    public static final String ACTION_REPORT_REJECTED = "REPORT_REJECTED";
    public static final String ACTION_REPORT_REVIEWING = "REPORT_REVIEWING";

    // Target types
    public static final String TARGET_EXAM = "EXAM";
    public static final String TARGET_QUESTION = "QUESTION";
    public static final String TARGET_REPORT = "REPORT";

    public void log(String action, Long actorId, String actorEmail,
                   String targetType, Long targetId, String targetTitle, String details) {
        try {
            ExamAuditLog entry = new ExamAuditLog();
            entry.setAction(action);
            entry.setActorId(actorId);
            entry.setActorEmail(actorEmail);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setTargetTitle(targetTitle);
            entry.setDetails(details);

            // Capture IP from request context
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                if (ip != null && ip.length() > 45) {
                    ip = ip.substring(0, 45);
                }
                entry.setIpAddress(ip);
            }

            auditLogRepository.save(entry);
        } catch (Exception e) {
            // Never let audit failure break the main flow
            log.warn("Failed to persist exam audit log: action={}, error={}", action, e.getMessage());
        }
    }

    public Page<ExamAuditLog> getAuditHistory(String targetType, Long targetId, Pageable pageable) {
        return auditLogRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId, pageable);
    }

    public Page<ExamAuditLog> getAuditHistoryByActor(Long actorId, Pageable pageable) {
        return auditLogRepository.findByActorIdOrderByCreatedAtDesc(actorId, pageable);
    }
}
