package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.dto.ExamResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class ExamFlowCacheService {

    private static final Duration ATTEMPT_VIEW_TTL = Duration.ofMinutes(5);
    private static final Duration QUESTION_BANK_TTL = Duration.ofMinutes(10);

    private final ConcurrentHashMap<Long, CacheEntry<ExamResponse>> attemptViewByExamId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, CacheEntry<QuestionBankSnapshot>> questionBankByExamId = new ConcurrentHashMap<>();

    public ExamResponse getOrLoadAttemptView(Long examId, Supplier<ExamResponse> loader) {
        return getOrLoad(attemptViewByExamId, examId, ATTEMPT_VIEW_TTL, loader);
    }

    public QuestionBankSnapshot getOrLoadQuestionBank(Long examId, Supplier<QuestionBankSnapshot> loader) {
        return getOrLoad(questionBankByExamId, examId, QUESTION_BANK_TTL, loader);
    }

    public void evictExam(Long examId) {
        if (examId == null) {
            return;
        }

        attemptViewByExamId.remove(examId);
        questionBankByExamId.remove(examId);
    }

    private <T> T getOrLoad(ConcurrentHashMap<Long, CacheEntry<T>> cache,
            Long key,
            Duration ttl,
            Supplier<T> loader) {
        Instant now = Instant.now();
        CacheEntry<T> cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.value();
        }

        T loaded = loader.get();
        cache.put(key, new CacheEntry<>(loaded, now.plus(ttl)));
        return loaded;
    }

    private record CacheEntry<T>(T value, Instant expiresAt) {
    }

    public record QuestionSnapshot(Long questionId,
            String content,
            Double scoreWeight) {
    }

    public record OptionSnapshot(Long optionId,
            Long questionId,
            String content,
            boolean isCorrect) {
    }

    public record QuestionBankSnapshot(List<QuestionSnapshot> questions,
            Map<Long, List<OptionSnapshot>> optionsByQuestionId,
            Map<Long, Set<Long>> correctOptionIdsByQuestionId,
            Set<Long> questionIds) {

        public QuestionBankSnapshot {
            questions = List.copyOf(questions);

            Map<Long, List<OptionSnapshot>> optionCopy = new HashMap<>();
            for (Map.Entry<Long, List<OptionSnapshot>> entry : optionsByQuestionId.entrySet()) {
                optionCopy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            optionsByQuestionId = Map.copyOf(optionCopy);

            Map<Long, Set<Long>> correctCopy = new HashMap<>();
            for (Map.Entry<Long, Set<Long>> entry : correctOptionIdsByQuestionId.entrySet()) {
                correctCopy.put(entry.getKey(), Set.copyOf(entry.getValue()));
            }
            correctOptionIdsByQuestionId = Map.copyOf(correctCopy);

            questionIds = Set.copyOf(questionIds);
        }

        public static QuestionBankSnapshot empty() {
            return new QuestionBankSnapshot(List.of(), Map.of(), Map.of(), Set.of());
        }

        public static QuestionBankSnapshot fromRaw(Collection<QuestionSnapshot> questionItems,
                Collection<OptionSnapshot> optionItems) {
            List<QuestionSnapshot> questions = new ArrayList<>(questionItems);
            Set<Long> questionIds = new HashSet<>();
            for (QuestionSnapshot question : questions) {
                questionIds.add(question.questionId());
            }

            Map<Long, List<OptionSnapshot>> optionsByQuestionId = new HashMap<>();
            Map<Long, Set<Long>> correctOptionIdsByQuestionId = new HashMap<>();

            for (OptionSnapshot option : optionItems) {
                optionsByQuestionId.computeIfAbsent(option.questionId(), ignored -> new ArrayList<>()).add(option);
                if (option.isCorrect()) {
                    correctOptionIdsByQuestionId.computeIfAbsent(option.questionId(), ignored -> new HashSet<>())
                            .add(option.optionId());
                }
            }

            return new QuestionBankSnapshot(questions, optionsByQuestionId, correctOptionIdsByQuestionId, questionIds);
        }
    }
}
