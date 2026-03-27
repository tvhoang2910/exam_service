package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.dto.CreateExamRequest;
import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.entity.OnlineExamStatus;
import com.exam_bank.exam_service.entity.Question;
import com.exam_bank.exam_service.entity.QuestionOption;
import com.exam_bank.exam_service.repository.ExamAttemptRepository;
import com.exam_bank.exam_service.repository.OnlineExamRepository;
import com.exam_bank.exam_service.repository.QuestionOptionRepository;
import com.exam_bank.exam_service.repository.QuestionRepository;
import com.exam_bank.exam_service.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamManagementServiceTest {

    @Mock
    private OnlineExamRepository examRepo;
    @Mock
    private ExamAttemptRepository examAttemptRepo;
    @Mock
    private QuestionRepository questionRepo;
    @Mock
    private QuestionOptionRepository optionRepo;
    @Mock
    private TagRepository tagRepo;
    @Mock
    private TagService tagService;
    @Mock
    private ExamFlowCacheService examFlowCacheService;

    @InjectMocks
    private ExamManagementService examManagementService;

    @Test
    void updateExam_shouldRejectWhenAttemptsExist() {
        Long examId = 3L;
        OnlineExam exam = new OnlineExam();
        exam.setId(examId);

        Question existingQuestion = new Question();
        existingQuestion.setId(99L);
        existingQuestion.setExam(exam);
        existingQuestion.setContent("Old question");
        existingQuestion.setScoreWeight(1.0);

        CreateExamRequest request = new CreateExamRequest();
        request.setTitle("Updated title");
        request.setQuestions(List.of());

        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(examAttemptRepo.countByExamId(examId)).thenReturn(1L);
        when(questionRepo.findByExamIdOrderByIdAsc(examId)).thenReturn(List.of(existingQuestion));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> examManagementService.updateExam(examId, request));

        assertTrue(exception.getReason().contains("Cannot update questions"));
        verify(examRepo, never()).save(any());
    }

    @Test
    void updateExam_shouldAllowMetadataUpdateWhenAttemptsExistAndQuestionsUnchanged() {
        Long examId = 4L;

        OnlineExam exam = new OnlineExam();
        exam.setId(examId);
        exam.setTitle("Old title");
        exam.setMaxAttempts(2);

        Question question = new Question();
        question.setId(10L);
        question.setExam(exam);
        question.setContent("2 + 2 = ?");
        question.setExplanation("Basic math");
        question.setScoreWeight(1.0);

        QuestionOption optionA = new QuestionOption();
        optionA.setQuestion(question);
        optionA.setContent("3");
        optionA.setIsCorrect(false);

        QuestionOption optionB = new QuestionOption();
        optionB.setQuestion(question);
        optionB.setContent("4");
        optionB.setIsCorrect(true);

        CreateExamRequest.OptionDto requestOptionA = new CreateExamRequest.OptionDto();
        requestOptionA.setContent("3");
        requestOptionA.setIsCorrect(false);

        CreateExamRequest.OptionDto requestOptionB = new CreateExamRequest.OptionDto();
        requestOptionB.setContent("4");
        requestOptionB.setIsCorrect(true);

        CreateExamRequest.QuestionDto requestQuestion = new CreateExamRequest.QuestionDto();
        requestQuestion.setContent("2 + 2 = ?");
        requestQuestion.setExplanation("Basic math");
        requestQuestion.setScoreWeight(1.0);
        requestQuestion.setOptions(List.of(requestOptionA, requestOptionB));

        CreateExamRequest request = new CreateExamRequest();
        request.setTitle("Updated title");
        request.setDescription("Updated desc");
        request.setDurationMinutes(30);
        request.setPassingScore(7);
        request.setMaxAttempts(5);
        request.setQuestions(List.of(requestQuestion));

        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(examAttemptRepo.countByExamId(examId)).thenReturn(2L);
        when(questionRepo.findByExamIdOrderByIdAsc(examId)).thenReturn(List.of(question));
        when(optionRepo.findByQuestionIdInOrderByIdAsc(List.of(10L))).thenReturn(List.of(optionA, optionB));
        when(examRepo.save(any(OnlineExam.class))).thenAnswer(invocation -> invocation.getArgument(0));

        examManagementService.updateExam(examId, request);

        verify(examRepo).save(any(OnlineExam.class));
        verify(questionRepo, never()).deleteByExamId(any());
        verify(optionRepo, never()).deleteByQuestionIdIn(any());
    }

    @Test
    void updateExam_shouldSkipQuestionRewriteWhenNoAttemptsAndQuestionsUnchanged() {
        Long examId = 5L;

        OnlineExam exam = new OnlineExam();
        exam.setId(examId);

        Question question = new Question();
        question.setId(11L);
        question.setExam(exam);
        question.setContent("Capital of France?");
        question.setExplanation("Geography");
        question.setScoreWeight(1.0);

        QuestionOption optionA = new QuestionOption();
        optionA.setQuestion(question);
        optionA.setContent("Paris");
        optionA.setIsCorrect(true);

        QuestionOption optionB = new QuestionOption();
        optionB.setQuestion(question);
        optionB.setContent("Lyon");
        optionB.setIsCorrect(false);

        CreateExamRequest.OptionDto requestOptionA = new CreateExamRequest.OptionDto();
        requestOptionA.setContent("Paris");
        requestOptionA.setIsCorrect(true);

        CreateExamRequest.OptionDto requestOptionB = new CreateExamRequest.OptionDto();
        requestOptionB.setContent("Lyon");
        requestOptionB.setIsCorrect(false);

        CreateExamRequest.QuestionDto requestQuestion = new CreateExamRequest.QuestionDto();
        requestQuestion.setContent("Capital of France?");
        requestQuestion.setExplanation("Geography");
        requestQuestion.setScoreWeight(1.0);
        requestQuestion.setOptions(List.of(requestOptionA, requestOptionB));

        CreateExamRequest request = new CreateExamRequest();
        request.setTitle("Updated metadata only");
        request.setDurationMinutes(50);
        request.setPassingScore(6);
        request.setMaxAttempts(7);
        request.setQuestions(List.of(requestQuestion));

        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(examAttemptRepo.countByExamId(examId)).thenReturn(0L);
        when(questionRepo.findByExamIdOrderByIdAsc(examId)).thenReturn(List.of(question));
        when(optionRepo.findByQuestionIdInOrderByIdAsc(List.of(11L))).thenReturn(List.of(optionA, optionB));
        when(examRepo.save(any(OnlineExam.class))).thenAnswer(invocation -> invocation.getArgument(0));

        examManagementService.updateExam(examId, request);

        verify(questionRepo, never()).deleteByExamId(any());
        verify(optionRepo, never()).deleteByQuestionIdIn(any());
    }

    @Test
    void deleteExam_shouldSoftDeleteWhenAttemptsExist() {
        Long examId = 3L;
        OnlineExam exam = new OnlineExam();
        exam.setId(examId);
        exam.setTitle("Sample exam");

        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(examAttemptRepo.countByExamId(examId)).thenReturn(2L);

        examManagementService.deleteExam(examId);

        verify(examRepo).save(exam);
        verify(examRepo, never()).delete(any());
        verify(examAttemptRepo, never()).deleteByExamId(any());
        verify(questionRepo, never()).deleteByExamId(any());
        verify(optionRepo, never()).deleteByQuestionIdIn(any());
        org.junit.jupiter.api.Assertions.assertEquals(OnlineExamStatus.ARCHIVED, exam.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(exam.getTitle().startsWith("[DELETED] "));
    }

    @Test
    void deleteExam_shouldHardDeleteWhenNoAttempts() {
        Long examId = 9L;
        OnlineExam exam = new OnlineExam();
        exam.setId(examId);

        Question question = new Question();
        question.setId(20L);
        question.setExam(exam);

        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(examAttemptRepo.countByExamId(examId)).thenReturn(0L);
        when(questionRepo.findByExamIdOrderByIdAsc(examId)).thenReturn(List.of(question));

        examManagementService.deleteExam(examId);

        verify(optionRepo).deleteByQuestionIdIn(List.of(20L));
        verify(questionRepo).deleteByExamId(eq(examId));
        verify(examRepo).delete(exam);
    }
}
