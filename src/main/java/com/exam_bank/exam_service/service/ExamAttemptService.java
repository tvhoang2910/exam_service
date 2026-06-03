package com.exam_bank.exam_service.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
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
import com.exam_bank.exam_service.dto.EssaySubmissionDetailResponse;
import com.exam_bank.exam_service.dto.EssaySubmissionSummaryResponse;
import com.exam_bank.exam_service.dto.ExamResponse;
import com.exam_bank.exam_service.dto.ExamSubmittedEvent;
import com.exam_bank.exam_service.dto.GradeAnswerRequest;
import com.exam_bank.exam_service.dto.ExamSubmittedEvent.QuestionAnswered;
import com.exam_bank.exam_service.dto.ExamSubmittedEvent.TagInfo;
import com.exam_bank.exam_service.dto.SaveAttemptAnswerRequest;
import com.exam_bank.exam_service.dto.SaveAttemptAnswersBatchRequest;
import com.exam_bank.exam_service.dto.StartAttemptRequest;
import com.exam_bank.exam_service.dto.StartAttemptResponse;
import com.exam_bank.exam_service.entity.AnswerStatus;
import com.exam_bank.exam_service.entity.ExamAttempt;
import com.exam_bank.exam_service.entity.ExamAttemptAnswer;
import com.exam_bank.exam_service.entity.ExamAttemptStatus;
import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.entity.OnlineExamStatus;
import com.exam_bank.exam_service.entity.Question;
import com.exam_bank.exam_service.entity.QuestionType;
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

        Integer totalQuestions = exam.getTotalQuestions();
        if (totalQuestions == null || totalQuestions <= 0) {
            log.warn("Reject startAttempt: exam {} has no questions (totalQuestions={})", exam.getId(),
                    totalQuestions);
            throw new ResponseStatusException(BAD_REQUEST, "Exam has no questions");
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
                exam.getId(), userId, completedAttemptStatuses());
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
            ExamFlowCacheService.QuestionSnapshot questionSnapshot = findQuestionSnapshot(questionBank, questionId)
                    .orElseThrow(
                            () -> new ResponseStatusException(FORBIDDEN, "Question does not belong to this attempt"));
            ExamAttemptAnswer answer = existingAnswerByQuestionId.get(questionId);

            if (answer == null) {
                answer = new ExamAttemptAnswer();
                answer.setAttempt(attemptRef);
                answer.setQuestion(questionRepository.getReferenceById(questionId));
            }

            if (questionSnapshot.questionType() == QuestionType.ESSAY) {
                answer.setSelectedOptionIds("");
                answer.setEssayAnswer(normalizeTextAnswer(resolveEssayAnswer(request)));
            } else {
                answer.setSelectedOptionIds(encodeOptionIds(resolveSelectedOptionIds(request)));
                answer.setEssayAnswer(null);
            }
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
                completedAttemptStatuses());
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
        boolean hasPendingEssay = false;

        for (ExamFlowCacheService.QuestionSnapshot question : questions) {
            ExamAttemptAnswer answer = answerByQuestionId.get(question.questionId());
            double maxScore = question.scoreWeight() == null ? 1.0 : question.scoreWeight();

            if (answer == null) {
                answer = new ExamAttemptAnswer();
                answer.setAttempt(attempt);
                answer.setQuestion(questionRepository.getReferenceById(question.questionId()));
                answer.setSelectedOptionIds("");
                answer.setAnswerChangeCount(0);
                answerByQuestionId.put(question.questionId(), answer);
            }

            if (question.questionType() == QuestionType.ESSAY) {
                hasPendingEssay = true;
                answer.setSelectedOptionIds("");
                answer.setEssayAnswer(normalizeTextAnswer(answer.getEssayAnswer()));
                answer.setIsCorrect(false);
                answer.setMaxScore(maxScore);
                answer.setEarnedScore(0.0);
                answer.setStatus(AnswerStatus.PENDING_REVIEW);
                examAttemptAnswerRepository.save(answer);

                scoreMax += maxScore;
                continue;
            }

            Set<Long> selectedIds = decodeOptionIds(answer.getSelectedOptionIds());
            Set<Long> correctIds = correctOptionIdsByQuestionId.getOrDefault(question.questionId(), Set.of());
            boolean isCorrect = !selectedIds.isEmpty() && selectedIds.equals(correctIds);
            double earnedScore = isCorrect ? maxScore : 0.0;

            answer.setEssayAnswer(null);
            answer.setFeedback(null);
            answer.setIsCorrect(isCorrect);
            answer.setMaxScore(maxScore);
            answer.setEarnedScore(earnedScore);
            answer.setStatus(AnswerStatus.AUTO_GRADED);
            examAttemptAnswerRepository.save(answer);

            scoreRaw += earnedScore;
            scoreMax += maxScore;
        }

        attempt.setSubmittedAt(Instant.now());
        attempt.setDurationSeconds(Duration.between(attempt.getStartedAt(), attempt.getSubmittedAt()).toSeconds());

        // Gọi hàm dùng chung để tính điểm
        updateAttemptScores(attempt, scoreRaw, scoreMax);

        attempt.setStatus(hasPendingEssay ? ExamAttemptStatus.PARTIALLY_GRADED : ExamAttemptStatus.GRADED);
        examAttemptRepository.save(attempt);

        createReviewEvents(attempt, questions, answerByQuestionId);
        publishExamSubmittedEvent(attempt, questionBank, answerByQuestionId);

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
            if (qs.questionType() == QuestionType.ESSAY) {
                continue;
            }
            ExamAttemptAnswer answer = answerByQuestionId.get(qs.questionId());
            String selectedOptionIds = encodeOptionIds(
                    decodeOptionIds(answer == null ? null : answer.getSelectedOptionIds()));
            String correctOptionIds = encodeOptionIds(questionBank.correctOptionIdsByQuestionId()
                    .getOrDefault(qs.questionId(), Set.of()));

            double weight = qs.scoreWeight() == null ? 1.0 : qs.scoreWeight(); // Tối ưu: Dùng biến chung

            QuestionAnswered qe = new QuestionAnswered();
            qe.setQuestionId(qs.questionId());
            qe.setIsCorrect(answer != null && Boolean.TRUE.equals(answer.getIsCorrect()));
            qe.setEarnedScore(answer == null ? 0.0 : answer.getEarnedScore());
            qe.setMaxScore(weight);
            qe.setSelectedOptionIds(selectedOptionIds);
            qe.setCorrectOptionIds(correctOptionIds);
            qe.setResponseTimeMs(answer == null ? null : answer.getResponseTimeMs());
            qe.setAnswerChangeCount(answer == null ? 0 : answer.getAnswerChangeCount());
            qe.setDifficulty(weight);

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
            if (question.questionType() == QuestionType.ESSAY) {
                continue;
            }
            ExamAttemptAnswer answer = answerByQuestionId.get(question.questionId());
            if (answer == null) {
                continue;
            }
            Set<Long> selectedIds = decodeOptionIds(answer.getSelectedOptionIds());
            int quality = mapQuality(answer, selectedIds);
            double weight = question.scoreWeight() == null ? 1.0 : question.scoreWeight();

            QuestionReviewEvent event = new QuestionReviewEvent();
            event.setUserId(attempt.getUserId());
            event.setItemId(question.questionId());
            event.setAttemptId(attempt.getId());
            event.setEvaluatedAt(attempt.getSubmittedAt() == null ? Instant.now() : attempt.getSubmittedAt());
            event.setQuality(quality);
            event.setIsCorrect(Boolean.TRUE.equals(answer.getIsCorrect()));
            event.setLatencyMs(answer.getResponseTimeMs());
            event.setTopicTagIds(topicTagIds);
            event.setDifficulty(weight);
            event.setSource("EXAM_SUBMISSION");
            events.add(event);

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

    private Question.Difficulty mapQualityToDifficulty(ExamAttemptAnswer answer) {
        if (answer == null) {
            return Question.Difficulty.MEDIUM;
        }
        int quality = mapQuality(answer, decodeOptionIds(answer.getSelectedOptionIds()));
        if (quality >= 4) {
            return Question.Difficulty.EASY;
        }
        if (quality >= 3) {
            return Question.Difficulty.MEDIUM;
        }
        return quality == 0 ? Question.Difficulty.VERY_HARD : Question.Difficulty.HARD;
    }

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
            double weight = question.scoreWeight() == null ? 1.0 : question.scoreWeight();

            AttemptResultResponse.QuestionResult item = new AttemptResultResponse.QuestionResult();
            item.setAnswerId(answer == null ? null : answer.getId());
            item.setQuestionId(question.questionId());
            item.setContent(question.content());
            item.setQuestionType(question.questionType());
            item.setAnswerStatus(answer == null ? null : answer.getStatus());
            item.setScore(weight);
            item.setMaxScore(weight);
            item.setEarnedScore(answer == null ? 0.0 : answer.getEarnedScore());
            item.setCorrect(answer != null && Boolean.TRUE.equals(answer.getIsCorrect()));
            item.setResponseTimeMs(answer == null ? null : answer.getResponseTimeMs());
            item.setAnswerChangeCount(answer == null ? 0 : answer.getAnswerChangeCount());
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
            item.setEssayAnswer(answer == null ? null : answer.getEssayAnswer());
            item.setTextAnswer(answer == null ? null : answer.getEssayAnswer());
            item.setFeedback(answer == null ? null : answer.getFeedback());
            item.setTeacherFeedback(answer == null ? null : answer.getFeedback());
            item.setSampleAnswer(question.sampleAnswer());
            item.setGradingGuide(question.gradingGuide());
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

        // Tối ưu: Loại bỏ các bước chuyển đổi Map dư thừa
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
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

    private String normalizeTextAnswer(String textAnswer) {
        if (textAnswer == null) {
            return null;
        }

        String trimmed = textAnswer.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
                        question.getQuestionType() == null ? QuestionType.MULTIPLE_CHOICE : question.getQuestionType(),
                        question.getScoreWeight(),
                        question.getSampleAnswer(),
                        question.getGradingGuide()))
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

    private void updateAttemptScores(ExamAttempt attempt, double scoreRaw, double scoreMax) {
        attempt.setScoreRaw(round2(scoreRaw));
        attempt.setScoreMax(round2(scoreMax));

        double scorePercent = scoreMax <= 0 ? 0.0 : (scoreRaw * 100.0 / scoreMax);
        attempt.setScorePercent(round2(scorePercent));

        int passingScore = attempt.getExam().getPassingScore() == null ? 0 : attempt.getExam().getPassingScore();
        double requiredRawScore = resolveRequiredRawScore(scoreMax, passingScore);
        attempt.setPassed(scoreRaw >= requiredRawScore);
    }

    @Transactional
    public void gradeAnswer(Long attemptId, Long answerId, Long contributorId, GradeAnswerRequest request) {
        gradeEssaySubmissionInternal(answerId, contributorId, request, attemptId);

        log.info("gradeAnswer: attemptId={}, answerId={}, contributorId={}, newScore={}",
                attemptId, answerId, contributorId, request.getScore());
    }

    @Transactional(readOnly = true)
    public List<EssaySubmissionSummaryResponse> getPendingEssaySubmissions(Long contributorId) {
        assertContributorId(contributorId);
        return examAttemptAnswerRepository.findEssaySubmissionsByStatus(AnswerStatus.PENDING_REVIEW)
                .stream()
                .map(this::toEssaySubmissionSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public EssaySubmissionDetailResponse getEssaySubmission(Long submissionId, Long contributorId) {
        assertContributorId(contributorId);
        ExamAttemptAnswer answer = examAttemptAnswerRepository.findEssaySubmissionById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Essay submission not found"));
        return toEssaySubmissionDetail(answer);
    }

    @Transactional
    public EssaySubmissionDetailResponse gradeEssaySubmission(
            Long submissionId,
            Long contributorId,
            GradeAnswerRequest request) {
        return gradeEssaySubmissionInternal(submissionId, contributorId, request, null);
    }

    private EssaySubmissionDetailResponse gradeEssaySubmissionInternal(
            Long submissionId,
            Long contributorId,
            GradeAnswerRequest request,
            Long expectedAttemptId) {
        assertContributorId(contributorId);

        ExamAttemptAnswer answer = examAttemptAnswerRepository.findEssaySubmissionForUpdate(submissionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Essay submission not found"));

        if (expectedAttemptId != null && !answer.getAttempt().getId().equals(expectedAttemptId)) {
            throw new ResponseStatusException(BAD_REQUEST, "Answer does not belong to this attempt");
        }

        if (answer.getStatus() != AnswerStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(CONFLICT, "Essay submission has already been graded");
        }

        double maxScore = answer.getMaxScore() == null ? 0.0 : answer.getMaxScore();
        double score = request.getScore() == null ? 0.0 : request.getScore();
        if (score > maxScore) {
            throw new ResponseStatusException(BAD_REQUEST, "Score must not exceed question max score");
        }

        answer.setEarnedScore(score);
        answer.setFeedback(normalizeTextAnswer(request.getFeedback()));
        answer.setIsCorrect(score > 0.0);
        answer.setStatus(AnswerStatus.MANUALLY_GRADED);
        examAttemptAnswerRepository.save(answer);

        recalculateAttemptAfterManualGrade(answer.getAttempt());

        log.info("gradeEssaySubmission: submissionId={}, contributorId={}, score={}/{}",
                submissionId, contributorId, score, maxScore);
        return toEssaySubmissionDetail(answer);
    }

    private void recalculateAttemptAfterManualGrade(ExamAttempt attempt) {
        List<ExamAttemptAnswer> allAnswers = examAttemptAnswerRepository
                .findByAttemptIdOrderByQuestionIdAsc(attempt.getId());
        double scoreRaw = 0.0;
        double scoreMax = 0.0;
        boolean hasPendingEssay = false;

        for (ExamAttemptAnswer answer : allAnswers) {
            scoreRaw += answer.getEarnedScore() == null ? 0.0 : answer.getEarnedScore();
            scoreMax += answer.getMaxScore() == null ? 0.0 : answer.getMaxScore();
            if (answer.getQuestion().getQuestionType() == QuestionType.ESSAY
                    && answer.getStatus() == AnswerStatus.PENDING_REVIEW) {
                hasPendingEssay = true;
            }
        }

        updateAttemptScores(attempt, scoreRaw, scoreMax);
        attempt.setStatus(hasPendingEssay ? ExamAttemptStatus.PARTIALLY_GRADED : ExamAttemptStatus.GRADED);
        examAttemptRepository.save(attempt);
    }

    private void assertContributorId(Long contributorId) {
        if (contributorId == null || contributorId <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing contributorId");
        }
    }

    private EssaySubmissionSummaryResponse toEssaySubmissionSummary(ExamAttemptAnswer answer) {
        ExamAttempt attempt = answer.getAttempt();
        OnlineExam exam = attempt.getExam();
        return EssaySubmissionSummaryResponse.builder()
                .id(answer.getId())
                .answerId(answer.getId())
                .attemptId(attempt.getId())
                .questionId(answer.getQuestion().getId())
                .studentId(attempt.getUserId())
                .studentName(resolveDisplayName(attempt.getUserId()))
                .examId(exam.getId())
                .examTitle(exam.getTitle())
                .submittedAt(attempt.getSubmittedAt())
                .score(answer.getEarnedScore())
                .maxScore(answer.getMaxScore())
                .status(answer.getStatus())
                .build();
    }

    private EssaySubmissionDetailResponse toEssaySubmissionDetail(ExamAttemptAnswer answer) {
        ExamAttempt attempt = answer.getAttempt();
        OnlineExam exam = attempt.getExam();
        Question question = answer.getQuestion();
        return EssaySubmissionDetailResponse.builder()
                .id(answer.getId())
                .answerId(answer.getId())
                .attemptId(attempt.getId())
                .questionId(question.getId())
                .studentId(attempt.getUserId())
                .studentName(resolveDisplayName(attempt.getUserId()))
                .examId(exam.getId())
                .examTitle(exam.getTitle())
                .submittedAt(attempt.getSubmittedAt())
                .questionContent(question.getContent())
                .essayAnswer(answer.getEssayAnswer())
                .sampleAnswer(question.getSampleAnswer())
                .gradingGuide(question.getGradingGuide())
                .score(answer.getEarnedScore())
                .maxScore(answer.getMaxScore())
                .feedback(answer.getFeedback())
                .status(answer.getStatus())
                .build();
    }

    private String resolveDisplayName(Long userId) {
        return authUserLookupClient.findDisplayNameByUserId(userId)
                .orElse("User #" + userId);
    }

    private Optional<ExamFlowCacheService.QuestionSnapshot> findQuestionSnapshot(
            ExamFlowCacheService.QuestionBankSnapshot questionBank,
            Long questionId) {
        return questionBank.questions()
                .stream()
                .filter(question -> question.questionId().equals(questionId))
                .findFirst();
    }

    private String resolveEssayAnswer(SaveAttemptAnswerRequest request) {
        return request.getEssayAnswer() != null ? request.getEssayAnswer() : request.getTextAnswer();
    }

    private Collection<Long> resolveSelectedOptionIds(SaveAttemptAnswerRequest request) {
        if (request.getSelectedOptionIds() != null && !request.getSelectedOptionIds().isEmpty()) {
            return request.getSelectedOptionIds();
        }

        if (request.getSelectedOptionId() != null) {
            return List.of(request.getSelectedOptionId());
        }

        return List.of();
    }

    private List<ExamAttemptStatus> completedAttemptStatuses() {
        return List.of(
                ExamAttemptStatus.SUBMITTED,
                ExamAttemptStatus.AUTO_SUBMITTED,
                ExamAttemptStatus.PARTIALLY_GRADED,
                ExamAttemptStatus.GRADED);
    }
}
