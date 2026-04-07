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

    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer redisContainer;
    private final ObjectMapper objectMapper;

    private final AtomicInteger activeAttempts = new AtomicInteger(0);
    private final AtomicInteger submissionsToday = new AtomicInteger(0);

    // role -> list of SseEmitters
    private final Map<String, List<SseEmitter>> roleEmitters = new ConcurrentHashMap<>();

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

            var dead = new java.util.ArrayList<SseEmitter>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("exam").data(data));
                } catch (Exception e) {
                    dead.add(emitter);
                }
            }
            emitters.removeAll(dead);
        }
    }

    public void registerEmitter(String role, SseEmitter emitter) {
        List<SseEmitter> list = roleEmitters.get(role);
        if (list != null)
            list.add(emitter);
    }

    public void removeEmitter(String role, SseEmitter emitter) {
        List<SseEmitter> list = roleEmitters.get(role);
        if (list != null)
            list.remove(emitter);
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
