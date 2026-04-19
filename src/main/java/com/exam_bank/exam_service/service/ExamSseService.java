package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.dto.internal.ExamSseEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamSseService implements MessageListener {

    private static final String EXAM_EVENTS_CHANNEL = "exam:events";
    private static final String ROLE_USER = "USER";

    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer redisContainer;
    private final ObjectMapper objectMapper;

    private final AtomicInteger activeAttempts = new AtomicInteger(0);
    private final AtomicInteger submissionsToday = new AtomicInteger(0);

    // role -> list of SseEmitters (ADMIN, CONTRIBUTOR)
    private final Map<String, List<SseEmitter>> roleEmitters = new ConcurrentHashMap<>();
    // uploaderId -> list of SseEmitters (USER)
    private final Map<Long, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        roleEmitters.put("ADMIN", new CopyOnWriteArrayList<>());
        roleEmitters.put("CONTRIBUTOR", new CopyOnWriteArrayList<>());
        redisContainer.addMessageListener(this, new ChannelTopic(EXAM_EVENTS_CHANNEL));
        log.info("ExamSseService initialized, subscribed to Redis channel: {}", EXAM_EVENTS_CHANNEL);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody());
        log.debug("Redis exam event received: {}", body);
        try {
            ExamSseEvent event = objectMapper.readValue(body, ExamSseEvent.class);
            broadcast(event);
        } catch (Exception e) {
            log.error("Failed to deserialize exam SSE event: {}", e.getMessage());
        }
    }

    private void broadcast(ExamSseEvent event) {
        for (String role : List.of("ADMIN", "CONTRIBUTOR")) {
            List<SseEmitter> emitters = roleEmitters.get(role);
            if (emitters == null)
                continue;

            String data;
            try {
                data = objectMapper.writeValueAsString(event);
            } catch (Exception e) {
                log.error("Failed to serialize exam SSE event: {}", e.getMessage());
                continue;
            }

            var dead = new ArrayList<SseEmitter>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("exam").data(data));
                } catch (Exception e) {
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {
                        // no-op
                    }
                    dead.add(emitter);
                }
            }
            emitters.removeAll(dead);
        }
    }

    public void registerEmitter(String role, SseEmitter emitter) {
        registerEmitter(role, null, emitter);
    }

    public void registerEmitter(String role, Long userId, SseEmitter emitter) {
        if (ROLE_USER.equals(role)) {
            if (userId == null) {
                log.warn("registerEmitter called with USER role but null userId, dropping");
                return;
            }
            userEmitters
                    .computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>())
                    .add(emitter);
            log.info("Registered USER SSE emitter for userId={}", userId);
            return;
        }
        List<SseEmitter> list = roleEmitters.get(role);
        if (list != null) {
            list.add(emitter);
        }
    }

    public void removeEmitter(String role, SseEmitter emitter) {
        removeEmitter(role, null, emitter);
    }

    public void removeEmitter(String role, Long userId, SseEmitter emitter) {
        if (ROLE_USER.equals(role)) {
            if (userId == null) {
                return;
            }
            List<SseEmitter> list = userEmitters.get(userId);
            if (list != null) {
                list.remove(emitter);
                if (list.isEmpty()) {
                    userEmitters.remove(userId, list);
                }
            }
            return;
        }
        List<SseEmitter> list = roleEmitters.get(role);
        if (list != null) {
            list.remove(emitter);
        }
    }

    public void sendToUser(Long userId, String eventName, Object payload) {
        if (userId == null) {
            return;
        }
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            log.info("sendToUser: no active emitters for userId={}", userId);
            return;
        }

        String data;
        try {
            data = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize SSE payload for userId={}: {}", userId, e.getMessage());
            return;
        }

        var dead = new ArrayList<SseEmitter>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {
                    // no-op
                }
                dead.add(emitter);
            }
        }
        emitters.removeAll(dead);
        log.info("Sent SSE event={} to userId={} (activeEmitters={})", eventName, userId,
                emitters.size());
    }

    private void publishEvent(ExamSseEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            redisTemplate.convertAndSend(EXAM_EVENTS_CHANNEL, json);
        } catch (Exception e) {
            log.error("Failed to publish exam event: {}", e.getMessage());
        }
    }

    public void onExamSubmitted(Long attemptId, Long examId, String examTitle, Long userId) {
        submissionsToday.incrementAndGet();
        ExamSseEvent event = ExamSseEvent.submitted(
                attemptId, examId, examTitle, userId,
                activeAttempts.get(), submissionsToday.get());
        publishEvent(event);
    }

    public void onAttemptStarted(Long attemptId, Long examId) {
        activeAttempts.incrementAndGet();
        ExamSseEvent event = ExamSseEvent.attemptStarted(
                attemptId, examId,
                activeAttempts.get(), submissionsToday.get());
        publishEvent(event);
    }

    public void onAttemptEnded(Long attemptId) {
        activeAttempts.decrementAndGet();
        ExamSseEvent event = ExamSseEvent.attemptEnded(
                attemptId,
                Math.max(0, activeAttempts.get()), submissionsToday.get());
        publishEvent(event);
    }

    public int getActiveAttemptCount() {
        return Math.max(0, activeAttempts.get());
    }

    public int getSubmissionsTodayCount() {
        return submissionsToday.get();
    }

    @Scheduled(cron = "0 0 0 * * *") // midnight daily
    public void resetDailyCounters() {
        submissionsToday.set(0);
        log.info("Reset daily submission counter at midnight");
    }
}
