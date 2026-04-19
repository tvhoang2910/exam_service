package com.exam_bank.exam_service.controller;

import com.exam_bank.exam_service.dto.internal.ExamSseEvent;
import com.exam_bank.exam_service.service.ExamSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
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
            @CookieValue(value = "access_token", required = false) String accessTokenCookie) {

        String token = extractToken(authHeader, accessTokenCookie);
        if (token == null) {
            log.warn("SSE exam events: no Bearer token");
            return new SseEmitter(0L);
        }

        String role;
        Long userId;
        try {
            Jwt jwt = jwtDecoder.decode(token);
            role = jwt.getClaimAsString("role");
            userId = extractUserId(jwt);
        } catch (Exception e) {
            log.warn("SSE exam events: failed to decode JWT: {}", e.getMessage());
            return new SseEmitter(0L);
        }

        if (role == null
                || (!role.equals("ADMIN") && !role.equals("CONTRIBUTOR") && !role.equals("USER"))) {
            log.warn("SSE exam events: role={} not allowed", role);
            return new SseEmitter(0L);
        }

        if (role.equals("USER") && userId == null) {
            log.warn("SSE exam events: USER role missing userId claim");
            return new SseEmitter(0L);
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        examSseService.registerEmitter(role, userId, emitter);

        final Long uId = userId;
        emitter.onCompletion(() -> {
            log.info("SSE exam events: emitter completed for role={} userId={}", role, uId);
            examSseService.removeEmitter(role, uId, emitter);
        });

        emitter.onTimeout(() -> {
            log.info("SSE exam events: emitter timed out for role={} userId={}", role, uId);
            examSseService.removeEmitter(role, uId, emitter);
        });

        emitter.onError(e -> {
            log.warn("SSE exam events: emitter error for role={} userId={}: {}", role, uId, e.getMessage());
            examSseService.removeEmitter(role, uId, emitter);
        });

        // Send initial SNAPSHOT (admins/contributors get global counters; users still get a hello frame)
        try {
            ExamSseEvent snapshot = ExamSseEvent.snapshot(
                    examSseService.getActiveAttemptCount(),
                    examSseService.getSubmissionsTodayCount());
            emitter.send(SseEmitter.event().name("exam").data(snapshot));
        } catch (IOException e) {
            log.warn("Failed to send exam SSE snapshot");
        }

        log.info("SSE exam events: connected for role={} userId={}", role, userId);
        return emitter;
    }

    private String extractToken(String authHeader, String accessTokenCookie) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        if (accessTokenCookie != null && !accessTokenCookie.isBlank()) {
            return accessTokenCookie;
        }
        return null;
    }

    private Long extractUserId(Jwt jwt) {
        Object claim = jwt.getClaims().get("userId");
        if (claim == null) {
            return null;
        }
        if (claim instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(claim));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
