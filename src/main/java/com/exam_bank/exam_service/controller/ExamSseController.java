package com.exam_bank.exam_service.controller;

import com.exam_bank.exam_service.dto.internal.ExamSseEvent;
import com.exam_bank.exam_service.service.ExamSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
@Slf4j
public class ExamSseController {

    private static final long SSE_TIMEOUT_MS = TimeUnit.HOURS.toMillis(8);

    private final ExamSseService examSseService;
    private final JwtDecoder jwtDecoder;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter examEventsSse(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {

        String token = extractToken(authHeader, tokenParam);
        if (token == null) {
            log.warn("SSE exam events: no Bearer token");
            return new SseEmitter(0L);
        }

        String role;
        try {
            Jwt jwt = jwtDecoder.decode(token);
            role = jwt.getClaimAsString("role");
        } catch (Exception e) {
            log.warn("SSE exam events: failed to decode JWT: {}", e.getMessage());
            return new SseEmitter(0L);
        }

        if (role == null || (!role.equals("ADMIN") && !role.equals("CONTRIBUTOR"))) {
            log.warn("SSE exam events: role={} not allowed", role);
            return new SseEmitter(0L);
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        examSseService.registerEmitter(role, emitter);

        emitter.onCompletion(() -> {
            log.info("SSE exam events: emitter completed for role={}", role);
            examSseService.removeEmitter(role, emitter);
        });

        emitter.onTimeout(() -> {
            log.info("SSE exam events: emitter timed out for role={}", role);
            examSseService.removeEmitter(role, emitter);
        });

        emitter.onError(e -> {
            log.warn("SSE exam events: emitter error for role={}: {}", role, e.getMessage());
            examSseService.removeEmitter(role, emitter);
        });

        // Send initial SNAPSHOT
        try {
            ExamSseEvent snapshot = ExamSseEvent.snapshot(
                    examSseService.getActiveAttemptCount(),
                    examSseService.getSubmissionsTodayCount());
            emitter.send(SseEmitter.event().name("exam").data(snapshot));
        } catch (IOException e) {
            log.warn("Failed to send exam SSE snapshot");
        }

        log.info("SSE exam events: connected for role={}", role);
        return emitter;
    }

    private String extractToken(String authHeader, String tokenParam) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        if (tokenParam != null && !tokenParam.isBlank()) {
            return tokenParam;
        }
        return null;
    }
}
