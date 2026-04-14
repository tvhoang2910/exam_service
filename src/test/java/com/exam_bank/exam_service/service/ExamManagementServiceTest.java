package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.entity.Question;
import com.exam_bank.exam_service.feature.reporting.repository.QuestionReportHistoryRepository;
import com.exam_bank.exam_service.feature.reporting.repository.QuestionReportRepository;
import com.exam_bank.exam_service.repository.ExamAttemptAnswerRepository;
import com.exam_bank.exam_service.repository.ExamAttemptRepository;
import com.exam_bank.exam_service.repository.OnlineExamRepository;
import com.exam_bank.exam_service.repository.QuestionOptionRepository;
import com.exam_bank.exam_service.repository.QuestionRepository;
import com.exam_bank.exam_service.repository.QuestionReviewEventRepository;
import com.exam_bank.exam_service.repository.Sm2RecordRepository;
import com.exam_bank.exam_service.repository.TagRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExamManagementService Unit Tests")
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
    private Sm2RecordRepository sm2RecordRepo;

    @Mock
    private ExamAttemptAnswerRepository examAttemptAnswerRepo;

    @Mock
    private QuestionReviewEventRepository questionReviewEventRepo;

    @Mock
    private QuestionReportRepository questionReportRepo;

    @Mock
    private QuestionReportHistoryRepository questionReportHistoryRepo;

    @Mock
    private TagRepository tagRepo;

    @Mock
    private TagService tagService;

    @Mock
    private ExamFlowCacheService examFlowCacheService;

    @Mock
    private ExamAuditService examAuditService;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @InjectMocks
    private ExamManagementService service;

    @Test
    @DisplayName("deleteExam removes SM2 records before deleting questions")
    void deleteExamRemovesSm2RecordsBeforeDeletingQuestions() {
        Long examId = 2L;
        Long questionId = 101L;
        List<Long> questionIds = List.of(questionId);

        OnlineExam exam = new OnlineExam();
        exam.setId(examId);
        exam.setTitle("Đề thi bị xóa");

        Question question = new Question();
        question.setId(questionId);

        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(questionRepo.findByExamIdOrderByIdAsc(examId)).thenReturn(List.of(question));
        when(examAttemptRepo.findIdsByExamId(examId)).thenReturn(List.of());
        when(questionReportRepo.findIdsByQuestionIdIn(questionIds)).thenReturn(List.of());
        when(authenticatedUserService.getCurrentUserId()).thenReturn(99L);

        service.deleteExam(examId);

        InOrder deleteOrder = inOrder(questionReportRepo, sm2RecordRepo, optionRepo, questionRepo, examRepo);
        deleteOrder.verify(questionReportRepo).deleteByQuestionIdIn(questionIds);
        deleteOrder.verify(sm2RecordRepo).deleteByQuestionIdIn(questionIds);
        deleteOrder.verify(optionRepo).deleteByQuestionIdIn(questionIds);
        deleteOrder.verify(questionRepo).deleteByExamId(examId);
        deleteOrder.verify(examRepo).delete(exam);

        verify(examFlowCacheService).evictExam(examId);
    }
}
