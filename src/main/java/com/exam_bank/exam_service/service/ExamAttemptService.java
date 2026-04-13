package com.exam_bank.exam_service.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.exam_bank.exam_service.dto.AttemptResultResponse;
import com.exam_bank.exam_service.dto.AttemptSummaryResponse;
import com.exam_bank.exam_service.dto.ExamResponse;
import com.exam_bank.exam_service.dto.ExamSubmittedEvent;
import com.exam_bank.exam_service.dto.ExamSubmittedEvent.QuestionAnswered;
import com.exam_bank.exam_service.dto.ExamSubmittedEvent.TagInfo;
import com.exam_bank.exam_service.dto.SaveAttemptAnswerRequest;
import com.exam_bank.exam_service.dto.SaveAttemptAnswersBatchRequest;
import com.exam_bank.exam_service.dto.StartAttemptRequest;
import com.exam_bank.exam_service.dto.StartAttemptResponse;
import com.exam_bank.exam_service.entity.ExamAttempt;
import com.exam_bank.exam_service.entity.ExamAttemptAnswer;
import com.exam_bank.exam_service.entity.ExamAttemptStatus;
import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.entity.OnlineExamStatus;
import com.exam_bank.exam_service.entity.Question;
import com.exam_bank.exam_service.entity.QuestionReviewEvent;
import com.exam_bank.exam_service.repository.ExamAttemptAnswerRepository;
import com.exam_bank.exam_service.repository.ExamAttemptRepository;
import com.exam_bank.exam_service.repository.OnlineExamRepository;
import com.exam_bank.exam_service.repository.QuestionOptionRepository;
import com.exam_bank.exam_service.repository.QuestionRepository;
import com.exam_bank.exam_service.repository.QuestionReviewEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamAttemptService {

    private static final long FAST_ANSWER_MS = 15_000;
    private static final long NORMAL_ANSWER_MS = 30_000;

    private final OnlineExamRepository examRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final ExamAttemptAnswerRepository examAttemptAnswerRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuestionReviewEventRepository questionReviewEventRepository;
    private final Sm2Service sm2Service;
    private final ExamManagementService examManagementService;
    private final ExamFlowCacheService examFlowCacheService;
    private final RabbitMQEventPublisher rabbitMQEventPublisher;
    private final AdminAlertPublisher adminAlertPublisher;
    private final ExamSseService examSseService;
    private final AuthUserLookupClient authUserLookupClient;
    private final AuthenticatedUserService authenticatedUserService;

    @Transactional(readOnly = true)
    public ExamResponse getAttemptView(Long examId) {
        OnlineExam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Exam not found"));

        if (exam.getStatus() != OnlineExamStatus.PUBLISHED) {
            throw new ResponseStatusException(NOT_FOUND, "Exam not found");
        }

        Optional<Long> userId = authenticatedUserService.getCurrentUserIdOptional();
        boolean premiumLocked = isPremiumExamLockedForUser(exam, userId.orElse(null));
        Integer questionLimit = premiumLocked ? resolveTeaserQuestionCount(exam) : null;
        return examManagementService.mapPublicAttemptView(exam, questionLimit, premiumLocked);
    }

    @Transactional
    public StartAttemptResponse startAttempt(StartAttemptRequest request, Long userId) {
        OnlineExam exam = examRepository.findById(request.getExamId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Exam not found"));

        if (exam.getStatus() != OnlineExamStatus.PUBLISHED) {
            log.warn("Start attempt rejected: exam {} is not published for user {}", exam.getId(), userId);
            throw new ResponseStatusException(BAD_REQUEST, "Exam is not available for attempts");
        }

        if (isPremiumExamLockedForUser(exam, userId)) {
            log.warn("Start attempt rejected: premium exam {} locked for user {}", exam.getId(), userId);
            throw new ResponseStatusException(FORBIDDEN,
                    "Premium exam requires an active Premium subscription. Please upgrade to continue.");
        }

        Optional<ExamAttempt> inProgressOpt = examAttemptRepository
                .findFirstByExamIdAndUserIdAndStatusOrderByCreatedAtDesc(exam.getId(), userId,
                        ExamAttemptStatus.IN_PROGRESS);

        if (inProgressOpt.isPresent()) {
            ExamAttempt inProgress = inProgressOpt.get();
            if (Instant.now().isBefore(inProgress.getExpiresAt())) {
                return toStartAttemptResponse(inProgress, exam);
            }
            finalizeAttempt(inProgress, true);
        }

        int maxAttempts = exam.getMaxAttempts() == null ? 100 : exam.getMaxAttempts();
        long submittedCount = examAttemptRepository.countByExamIdAndUserIdAndStatusIn(
                exam.getId(), userId, List.of(ExamAttemptStatus.SUBMITTED, ExamAttemptStatus.AUTO_SUBMITTED));
        if (submittedCount >= maxAttempts) {
            log.warn("Start attempt rejected: exam {} user {} submittedCount={} maxAttempts={}",
                    exam.getId(), userId, submittedCount, maxAttempts);
            throw new ResponseStatusException(BAD_REQUEST,
                    "You reached the maximum number of attempts for this exam (" + submittedCount + "/"
                            + maxAttempts + ")");
        }

        Instant now = Instant.now();
        int durationMinutes = exam.getDurationMinutes() == null ? 60 : exam.getDurationMinutes();

        ExamAttempt attempt = new ExamAttempt();
        attempt.setExam(exam);
        attempt.setUserId(userId);
        attempt.setStatus(ExamAttemptStatus.IN_PROGRESS);
        attempt.setStartedAt(now);
        attempt.setExpiresAt(now.plus(Duration.ofMinutes(durationMinutes)));
        attempt.setClientVersion(request.getClientVersion());
        attempt.setSource("WEB");

        ExamAttempt saved = examAttemptRepository.save(attempt);
        examSseService.onAttemptStarted(saved.getId(), exam.getId());
        return toStartAttemptResponse(saved, exam);
    }

    @Transactional
    public void saveAnswer(Long attemptId, Long userId, SaveAttemptAnswerRequest request) {
        saveAnswers(attemptId, userId, List.of(request));
    }

    @Transactional
    public void saveAnswersBatch(Long attemptId, Long userId, SaveAttemptAnswersBatchRequest request) {
        saveAnswers(attemptId, userId, request.getAnswers());
    }

    private void saveAnswers(Long attemptId, Long userId, List<SaveAttemptAnswerRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        ExamAttemptRepository.AttemptSaveContext saveContext = examAttemptRepository
                .findSaveContext(attemptId, userId, ExamAttemptStatus.IN_PROGRESS)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Attempt already submitted"));

        if (Instant.now().isAfter(saveContext.getExpiresAt())) {
            throw new ResponseStatusException(BAD_REQUEST, "Attempt expired. Please submit to get result");
        }

        Map<Long, SaveAttemptAnswerRequest> latestRequestByQuestionId = new LinkedHashMap<>();
        for (SaveAttemptAnswerRequest request : requests) {
            latestRequestByQuestionId.put(request.getQuestionId(), request);
        }

        List<Long> questionIds = new ArrayList<>(latestRequestByQuestionId.keySet());
        ExamFlowCacheService.QuestionBankSnapshot questionBank = examFlowCacheService.getOrLoadQuestionBank(
                saveContext.getExamId(),
                () -> loadQuestionBankSnapshot(saveContext.getExamId()));

        for (Long questionId : questionIds) {
            if (!questionBank.questionIds().contains(questionId)) {
                throw new ResponseStatusException(FORBIDDEN, "Question does not belong to this attempt");
            }
        }

        Map<Long, ExamAttemptAnswer> existingAnswerByQuestionId = examAttemptAnswerRepository
                .findByAttemptIdAndQuestionIdIn(saveContext.getAttemptId(), questionIds)
                .stream()
                .collect(Collectors.toMap(answer -> answer.getQuestion().getId(), answer -> answer));

        ExamAttempt attemptRef = examAttemptRepository.getReferenceById(saveContext.getAttemptId());
        List<ExamAttemptAnswer> answersToSave = new ArrayList<>(questionIds.size());

        for (Long questionId : questionIds) {
            SaveAttemptAnswerRequest request = latestRequestByQuestionId.get(questionId);
            ExamAttemptAnswer answer = existingAnswerByQuestionId.get(questionId);

            if (answer == null) {
                answer = new ExamAttemptAnswer();
                answer.setAttempt(attemptRef);
                answer.setQuestion(questionRepository.getReferenceById(questionId));
            }

            answer.setSelectedOptionIds(encodeOptionIds(request.getSelectedOptionIds()));
            answer.setResponseTimeMs(request.getResponseTimeMs());
            answer.setAnswerChangeCount(request.getAnswerChangeCount() == null ? 0 : request.getAnswerChangeCount());
            answersToSave.add(answer);
        }

        examAttemptAnswerRepository.saveAll(answersToSave);
    }

    @Transactional
    public AttemptResultResponse submitAttempt(Long attemptId, Long userId) {
        ExamAttempt attempt = getAttemptOwnedByUser(attemptId, userId);
        if (attempt.getStatus() != ExamAttemptStatus.IN_PROGRESS) {
            return buildAttemptResult(attempt, null, null);
        }

        boolean autoSubmitted = Instant.now().isAfter(attempt.getExpiresAt());
        ExamFlowCacheService.QuestionBankSnapshot questionBank = finalizeAttempt(attempt, autoSubmitted);
        Map<Long, ExamAttemptAnswer> answerByQuestionId = examAttemptAnswerRepository
                .findByAttemptIdOrderByQuestionIdAsc(attempt.getId())
                .stream()
                .collect(Collectors.toMap(answer -> answer.getQuestion().getId(), answer -> answer, (a, b) -> a));
        return buildAttemptResult(attempt, questionBank, answerByQuestionId);
    }

    @Transactional(readOnly = true)
    public AttemptResultResponse getAttemptResult(Long attemptId, Long userId) {
        ExamAttempt attempt = getAttemptOwnedByUser(attemptId, userId);
        if (attempt.getStatus() == ExamAttemptStatus.IN_PROGRESS && Instant.now().isAfter(attempt.getExpiresAt())) {
            throw new ResponseStatusException(BAD_REQUEST, "Attempt expired and pending submission");
        }
        return buildAttemptResult(attempt, null, null);
    }

    @Transactional(readOnly = true)
    public List<AttemptSummaryResponse> getAttemptHistory(Long userId) {
        List<ExamAttempt> attempts = examAttemptRepository.findSubmittedHistoryByUserId(
                userId,
                List.of(ExamAttemptStatus.SUBMITTED, ExamAttemptStatus.AUTO_SUBMITTED));
        return attempts.stream().map(this::toAttemptSummary).toList();
    }

    private StartAttemptResponse toStartAttemptResponse(ExamAttempt attempt, OnlineExam exam) {
        StartAttemptResponse response = new StartAttemptResponse();
        response.setAttemptId(attempt.getId());
        response.setExamId(exam.getId());
        response.setStartedAt(attempt.getStartedAt());
        response.setExpiresAt(attempt.getExpiresAt());
        response.setDurationMinutes(exam.getDurationMinutes());
        response.setMaxAttempts(exam.getMaxAttempts());
        return response;
    }

    private ExamAttempt getAttemptOwnedByUser(Long attemptId, Long userId) {
        return examAttemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Attempt not found"));
    }

    private ExamFlowCacheService.QuestionBankSnapshot finalizeAttempt(ExamAttempt attempt, boolean autoSubmitted) {
        examSseService.onAttemptEnded(attempt.getId());
        ExamFlowCacheService.QuestionBankSnapshot questionBank = examFlowCacheService.getOrLoadQuestionBank(
                attempt.getExam().getId(),
                () -> loadQuestionBankSnapshot(attempt.getExam().getId()));

        List<ExamFlowCacheService.QuestionSnapshot> questions = questionBank.questions();
        if (questions.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Exam has no questions");
        }
        Map<Long, Set<Long>> correctOptionIdsByQuestionId = questionBank.correctOptionIdsByQuestionId();

        Map<Long, ExamAttemptAnswer> answerByQuestionId = examAttemptAnswerRepository
                .findByAttemptIdOrderByQuestionIdAsc(attempt.getId())
                .stream()
                .collect(Collectors.toMap(answer -> answer.getQuestion().getId(), answer -> answer, (a, b) -> a));

        double scoreRaw = 0.0;
        double scoreMax = 0.0;

        for (ExamFlowCacheService.QuestionSnapshot question : questions) {
            Set<Long> selectedIds = decodeOptionIds(
                    Optional.ofNullable(answerByQuestionId.get(question.questionId()))
                            .map(ExamAttemptAnswer::getSelectedOptionIds)
                            .orElse(null));
            Set<Long> correctIds = correctOptionIdsByQuestionId.getOrDefault(question.questionId(), Set.of());
            double maxScore = question.scoreWeight() == null ? 1.0 : question.scoreWeight();
            boolean isCorrect = !selectedIds.isEmpty() && selectedIds.equals(correctIds);
            double earnedScore = isCorrect ? maxScore : 0.0;

            ExamAttemptAnswer answer = answerByQuestionId.get(question.questionId());
            if (answer == null) {
                answer = new ExamAttemptAnswer();
                answer.setAttempt(attempt);
                answer.setQuestion(questionRepository.getReferenceById(question.questionId()));
                answer.setSelectedOptionIds("");
                answer.setAnswerChangeCount(0);
                answerByQuestionId.put(question.questionId(), answer);
            }

            answer.setIsCorrect(isCorrect);
            answer.setMaxScore(maxScore);
            answer.setEarnedScore(earnedScore);
            examAttemptAnswerRepository.save(answer);

            scoreRaw += earnedScore;
            scoreMax += maxScore;
        }

        attempt.setSubmittedAt(Instant.now());
        attempt.setDurationSeconds(Duration.between(attempt.getStartedAt(), attempt.getSubmittedAt()).toSeconds());
        attempt.setScoreRaw(round2(scoreRaw));
        attempt.setScoreMax(round2(scoreMax));
        double scorePercent = scoreMax <= 0 ? 0.0 : (scoreRaw * 100.0 / scoreMax);
        attempt.setScorePercent(round2(scorePercent));
        int passingScore = attempt.getExam().getPassingScore() == null ? 0 : attempt.getExam().getPassingScore();
        double requiredRawScore = resolveRequiredRawScore(scoreMax, passingScore);
        attempt.setPassed(scoreRaw >= requiredRawScore);
        attempt.setStatus(autoSubmitted ? ExamAttemptStatus.AUTO_SUBMITTED : ExamAttemptStatus.SUBMITTED);
        examAttemptRepository.save(attempt);

        createReviewEvents(attempt, questions, answerByQuestionId);
        publishExamSubmittedEvent(attempt, questionBank, answerByQuestionId);

        // Publish admin alert
        String userDisplayName = authUserLookupClient.findDisplayNameByUserId(attempt.getUserId())
                .orElse("Thành viên");
        adminAlertPublisher.publishExamSubmittedAlert(
                userDisplayName,
                attempt.getExam().getId(),
                attempt.getExam().getTitle(),
                attempt.getId());

        return questionBank;
    }

    private void publishExamSubmittedEvent(ExamAttempt attempt,
            ExamFlowCacheService.QuestionBankSnapshot questionBank,
            Map<Long, ExamAttemptAnswer> answerByQuestionId) {
        ExamSubmittedEvent event = new ExamSubmittedEvent();
        event.setAttemptId(attempt.getId());
        event.setUserId(attempt.getUserId());
        event.setExamId(attempt.getExam().getId());
        event.setExamTitle(attempt.getExam().getTitle());
        event.setSubmittedAt(attempt.getSubmittedAt());
        event.setScoreRaw(attempt.getScoreRaw());
        event.setScoreMax(attempt.getScoreMax());
        event.setScorePercent(attempt.getScorePercent());
        event.setDurationSeconds(attempt.getDurationSeconds());

        List<QuestionAnswered> questionEvents = new ArrayList<>();
        for (ExamFlowCacheService.QuestionSnapshot qs : questionBank.questions()) {
            ExamAttemptAnswer answer = answerByQuestionId.get(qs.questionId());
            String selectedOptionIds = encodeOptionIds(
                    decodeOptionIds(answer == null ? null : answer.getSelectedOptionIds()));
            String correctOptionIds = encodeOptionIds(questionBank.correctOptionIdsByQuestionId()
                    .getOrDefault(qs.questionId(), Set.of()));

            QuestionAnswered qe = new QuestionAnswered();
            qe.setQuestionId(qs.questionId());
            qe.setIsCorrect(Boolean.TRUE.equals(answer != null ? answer.getIsCorrect() : null));
            qe.setEarnedScore(answer == null ? 0.0 : answer.getEarnedScore());
            qe.setMaxScore(qs.scoreWeight() == null ? 1.0 : qs.scoreWeight());
            qe.setSelectedOptionIds(selectedOptionIds);
            qe.setCorrectOptionIds(correctOptionIds);
            qe.setResponseTimeMs(answer == null ? null : answer.getResponseTimeMs());
            qe.setAnswerChangeCount(answer == null ? 0 : answer.getAnswerChangeCount());
            qe.setDifficulty(qs.scoreWeight() == null ? 1.0 : qs.scoreWeight());
            // tagIds from exam tags
            if (attempt.getExam().getTags() != null) {
                qe.setTagIds(attempt.getExam().getTags().stream()
                        .map(tag -> String.valueOf(tag.getId()))
                        .sorted()
                        .collect(Collectors.joining(",")));
            }
            questionEvents.add(qe);
        }
        event.setQuestions(questionEvents);

        List<TagInfo> tagInfos = attempt.getExam().getTags() == null ? List.of()
                : attempt.getExam().getTags().stream()
                        .map(tag -> {
                            TagInfo ti = new TagInfo();
                            ti.setTagId(tag.getId());
                            ti.setTagName(tag.getName());
                            return ti;
                        })
                        .toList();
        event.setExamTags(tagInfos);

        rabbitMQEventPublisher.publishExamSubmitted(event);
    }

    private void createReviewEvents(ExamAttempt attempt,
            List<ExamFlowCacheService.QuestionSnapshot> questions,
            Map<Long, ExamAttemptAnswer> answerByQuestionId) {
        questionReviewEventRepository.deleteByAttemptId(attempt.getId());

        String topicTagIds = attempt.getExam().getTags() == null
                ? ""
                : attempt.getExam().getTags().stream()
                        .map(tag -> String.valueOf(tag.getId()))
                        .sorted()
                        .collect(Collectors.joining(","));

        List<QuestionReviewEvent> events = new ArrayList<>();
        for (ExamFlowCacheService.QuestionSnapshot question : questions) {
            ExamAttemptAnswer answer = answerByQuestionId.get(question.questionId());
            Set<Long> selectedIds = decodeOptionIds(answer.getSelectedOptionIds());
            int quality = mapQuality(answer, selectedIds);

            QuestionReviewEvent event = new QuestionReviewEvent();
            event.setUserId(attempt.getUserId());
            event.setItemId(question.questionId());
            event.setAttemptId(attempt.getId());
            event.setEvaluatedAt(attempt.getSubmittedAt() == null ? Instant.now() : attempt.getSubmittedAt());
            event.setQuality(quality);
            event.setIsCorrect(Boolean.TRUE.equals(answer.getIsCorrect()));
            event.setLatencyMs(answer.getResponseTimeMs());
            event.setTopicTagIds(topicTagIds);
            event.setDifficulty(question.scoreWeight() == null ? 1.0 : question.scoreWeight());
            event.setSource("EXAM_SUBMISSION");
            events.add(event);

            // Record SM2 spaced-repetition
            sm2Service.recordAttempt(attempt.getUserId(), question.questionId(), quality);
        }

        questionReviewEventRepository.saveAll(events);
    }

    private int mapQuality(ExamAttemptAnswer answer, Set<Long> selectedIds) {
        if (selectedIds.isEmpty()) {
            return 0;
        }

        if (!Boolean.TRUE.equals(answer.getIsCorrect())) {
            return 1;
        }

        long latency = answer.getResponseTimeMs() == null ? Long.MAX_VALUE : answer.getResponseTimeMs();
        int changes = answer.getAnswerChangeCount() == null ? 0 : answer.getAnswerChangeCount();

        if (latency <= FAST_ANSWER_MS && changes == 0) {
            return 5;
        }

        if (latency <= NORMAL_ANSWER_MS) {
            return 4;
        }

        return 3;
    }

    /**
     * Map answer quality to user-specific difficulty based on performance in this
     * attempt.
     * Unlike the global Question.difficulty (which needs ≥10 historical attempts),
     * this is per-attempt difficulty so users immediately see feedback.
     */
    private Question.Difficulty mapQualityToDifficulty(ExamAttemptAnswer answer) {
        if (answer == null) {
            return Question.Difficulty.MEDIUM;
        }
        int quality = mapQuality(answer, decodeOptionIds(answer.getSelectedOptionIds()));
        // quality 5 = fast & correct → Easy for this user
        if (quality >= 4) {
            return Question.Difficulty.EASY;
        }
        // quality 3 = slow but correct → Medium for this user
        if (quality >= 3) {
            return Question.Difficulty.MEDIUM;
        }
        // quality 1 = wrong → Hard for this user
        // quality 0 = skipped → Very Hard for this user
        return quality == 0 ? Question.Difficulty.VERY_HARD : Question.Difficulty.HARD;
    }

    // Overload: use pre-loaded data (avoids redundant DB query in submitAttempt
    // path)
    private AttemptResultResponse buildAttemptResult(ExamAttempt attempt,
            ExamFlowCacheService.QuestionBankSnapshot questionBank,
            Map<Long, ExamAttemptAnswer> answerByQuestionId) {
        if (questionBank == null) {
            questionBank = examFlowCacheService.getOrLoadQuestionBank(
                    attempt.getExam().getId(),
                    () -> loadQuestionBankSnapshot(attempt.getExam().getId()));
        }
        if (answerByQuestionId == null) {
            answerByQuestionId = examAttemptAnswerRepository
                    .findByAttemptIdOrderByQuestionIdAsc(attempt.getId())
                    .stream()
                    .collect(Collectors.toMap(answer -> answer.getQuestion().getId(), answer -> answer, (a, b) -> a));
        }
        return doBuildResult(attempt, questionBank, answerByQuestionId);
    }

    private AttemptResultResponse doBuildResult(ExamAttempt attempt,
            ExamFlowCacheService.QuestionBankSnapshot questionBank,
            Map<Long, ExamAttemptAnswer> answerByQuestionId) {
        AttemptResultResponse response = new AttemptResultResponse();
        response.setAttemptId(attempt.getId());
        response.setExamId(attempt.getExam().getId());
        response.setExamTitle(attempt.getExam().getTitle());
        response.setStatus(attempt.getStatus());
        response.setStartedAt(attempt.getStartedAt());
        response.setSubmittedAt(attempt.getSubmittedAt());
        response.setDurationSeconds(attempt.getDurationSeconds());
        response.setScoreRaw(attempt.getScoreRaw());
        response.setScoreMax(attempt.getScoreMax());
        response.setScorePercent(attempt.getScorePercent());
        response.setPassingScore(attempt.getExam().getPassingScore());
        response.setPassed(attempt.getPassed());

        List<AttemptResultResponse.QuestionResult> questionResults = new ArrayList<>();
        for (ExamFlowCacheService.QuestionSnapshot question : questionBank.questions()) {
            ExamAttemptAnswer answer = answerByQuestionId.get(question.questionId());
            AttemptResultResponse.QuestionResult item = new AttemptResultResponse.QuestionResult();
            item.setQuestionId(question.questionId());
            item.setContent(question.content());
            item.setMaxScore(question.scoreWeight() == null ? 1.0 : question.scoreWeight());
            item.setEarnedScore(answer == null ? 0.0 : answer.getEarnedScore());
            item.setCorrect(answer != null && Boolean.TRUE.equals(answer.getIsCorrect()));
            item.setResponseTimeMs(answer == null ? null : answer.getResponseTimeMs());
            item.setAnswerChangeCount(answer == null ? 0 : answer.getAnswerChangeCount());
            // Độ khó cá nhân: dựa trên quality của user cho câu này
            item.setDifficulty(mapQualityToDifficulty(answer));

            List<AttemptResultResponse.OptionResult> optionResults = questionBank.optionsByQuestionId()
                    .getOrDefault(question.questionId(), List.of())
                    .stream()
                    .map(option -> {
                        AttemptResultResponse.OptionResult optionResult = new AttemptResultResponse.OptionResult();
                        optionResult.setId(option.optionId());
                        optionResult.setContent(option.content());
                        return optionResult;
                    })
                    .toList();
            item.setOptions(optionResults);

            item.setSelectedOptionIds(
                    new ArrayList<>(decodeOptionIds(answer == null ? null : answer.getSelectedOptionIds())));
            List<Long> correctOptionIds = questionBank.correctOptionIdsByQuestionId()
                    .getOrDefault(question.questionId(), Set.of())
                    .stream()
                    .sorted(Comparator.naturalOrder())
                    .toList();
            item.setCorrectOptionIds(correctOptionIds);
            questionResults.add(item);
        }

        response.setQuestionResults(questionResults);
        return response;
    }

    private AttemptSummaryResponse toAttemptSummary(ExamAttempt attempt) {
        AttemptSummaryResponse summary = new AttemptSummaryResponse();
        summary.setAttemptId(attempt.getId());
        summary.setExamId(attempt.getExam().getId());
        summary.setExamTitle(attempt.getExam().getTitle());
        summary.setStatus(attempt.getStatus());
        summary.setStartedAt(attempt.getStartedAt());
        summary.setSubmittedAt(attempt.getSubmittedAt());
        summary.setScoreRaw(attempt.getScoreRaw());
        summary.setScoreMax(attempt.getScoreMax());
        summary.setScorePercent(attempt.getScorePercent());
        summary.setPassed(attempt.getPassed());
        return summary;
    }

    private String encodeOptionIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }

        return ids.stream()
                .filter(Objects::nonNull)
                .map(Long::valueOf)
                .collect(Collectors.toCollection(HashSet::new))
                .stream()
                .sorted(Comparator.naturalOrder())
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private Set<Long> decodeOptionIds(String encodedIds) {
        if (encodedIds == null || encodedIds.isBlank()) {
            return Set.of();
        }

        Set<Long> ids = new HashSet<>();
        String[] parts = encodedIds.split(",");
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // skip invalid persisted value
            }
        }
        return ids;
    }

    private ExamFlowCacheService.QuestionBankSnapshot loadQuestionBankSnapshot(Long examId) {
        List<Question> questions = questionRepository.findByExamIdAndIsHiddenFalseOrderByIdAsc(examId);
        if (questions.isEmpty()) {
            return ExamFlowCacheService.QuestionBankSnapshot.empty();
        }

        List<ExamFlowCacheService.QuestionSnapshot> questionSnapshots = questions.stream()
                .map(question -> new ExamFlowCacheService.QuestionSnapshot(
                        question.getId(),
                        question.getContent(),
                        question.getScoreWeight()))
                .toList();

        List<Long> questionIds = questions.stream().map(Question::getId).toList();
        List<ExamFlowCacheService.OptionSnapshot> optionSnapshots = questionOptionRepository
                .findByQuestionIdInOrderByIdAsc(questionIds)
                .stream()
                .map(option -> new ExamFlowCacheService.OptionSnapshot(
                        option.getId(),
                        option.getQuestion().getId(),
                        option.getContent(),
                        Boolean.TRUE.equals(option.getIsCorrect())))
                .toList();

        return ExamFlowCacheService.QuestionBankSnapshot.fromRaw(questionSnapshots, optionSnapshots);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double resolveRequiredRawScore(double scoreMax, int passingScore) {
        if (passingScore <= 0) {
            return 0.0;
        }

        // passingScore in [1..10] is treated as a traditional 10-point threshold.
        if (passingScore <= 10) {
            return scoreMax * ((passingScore * 1.0) / 10.0);
        }

        // For larger values, keep backward-compatible absolute-point semantics.
        return passingScore;
    }

    private boolean isPremiumExamLockedForUser(OnlineExam exam, Long userId) {
        if (!Boolean.TRUE.equals(exam.getIsPremium())) {
            return false;
        }

        return !isPremiumUser(userId);
    }

    private boolean isPremiumUser(Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }

        return authUserLookupClient.findPremiumStatusByUserId(userId)
                .orElse(false);
    }

    private int resolveTeaserQuestionCount(OnlineExam exam) {
        Integer configured = exam.getTeaserQuestionCount();
        if (configured == null) {
            return 2;
        }

        return Math.max(1, Math.min(2, configured));
    }
}
