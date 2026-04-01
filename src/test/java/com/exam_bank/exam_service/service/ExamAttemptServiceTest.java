package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.repository.ExamAttemptAnswerRepository;
import com.exam_bank.exam_service.repository.ExamAttemptRepository;
import com.exam_bank.exam_service.repository.OnlineExamRepository;
import com.exam_bank.exam_service.repository.QuestionOptionRepository;
import com.exam_bank.exam_service.repository.QuestionRepository;
import com.exam_bank.exam_service.repository.QuestionReviewEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ExamAttemptServiceTest {

    @Mock
    private OnlineExamRepository examRepository;

    @Mock
    private ExamAttemptRepository examAttemptRepository;

    @Mock
    private ExamAttemptAnswerRepository examAttemptAnswerRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuestionOptionRepository questionOptionRepository;

    @Mock
    private QuestionReviewEventRepository questionReviewEventRepository;

    @Mock
    private ExamManagementService examManagementService;

    @Mock
    private ExamFlowCacheService examFlowCacheService;

    @Mock
    private RabbitMQEventPublisher rabbitMQEventPublisher;

    @InjectMocks
    private ExamAttemptService examAttemptService;

    @Test
    void resolveRequiredRawScore_shouldUseFractionWhenPassingScoreOnTenPointScale() throws Exception {
        Method method = ExamAttemptService.class.getDeclaredMethod("resolveRequiredRawScore", double.class, int.class);
        method.setAccessible(true);

        double requiredRawScore = (double) method.invoke(examAttemptService, 100.0, 7);

        assertThat(requiredRawScore).isEqualTo(70.0);
    }

    @Test
    void resolveRequiredRawScore_shouldKeepAbsolutePointsForLegacyLargePassingScores() throws Exception {
        Method method = ExamAttemptService.class.getDeclaredMethod("resolveRequiredRawScore", double.class, int.class);
        method.setAccessible(true);

        double requiredRawScore = (double) method.invoke(examAttemptService, 100.0, 65);

        assertThat(requiredRawScore).isEqualTo(65.0);
    }

    @Test
    void resolveRequiredRawScore_shouldReturnZeroWhenPassingScoreIsZeroOrNegative() throws Exception {
        Method method = ExamAttemptService.class.getDeclaredMethod("resolveRequiredRawScore", double.class, int.class);
        method.setAccessible(true);

        assertThat((double) method.invoke(examAttemptService, 100.0, 0)).isEqualTo(0.0);
        assertThat((double) method.invoke(examAttemptService, 100.0, -5)).isEqualTo(0.0);
    }

    @Test
    void resolveRequiredRawScore_shouldHandleMaxTenPointScale() throws Exception {
        Method method = ExamAttemptService.class.getDeclaredMethod("resolveRequiredRawScore", double.class, int.class);
        method.setAccessible(true);

        // passingScore = 10 → need 100% of scoreMax
        assertThat((double) method.invoke(examAttemptService, 50.0, 10)).isEqualTo(50.0);
        // passingScore = 1 → need 10% of scoreMax
        assertThat((double) method.invoke(examAttemptService, 50.0, 1)).isEqualTo(5.0);
    }

    @Test
    void resolveRequiredRawScore_shouldHandleFractionalResults() throws Exception {
        Method method = ExamAttemptService.class.getDeclaredMethod("resolveRequiredRawScore", double.class, int.class);
        method.setAccessible(true);

        // 3/10 of 33.33... = 10.0
        assertThat((double) method.invoke(examAttemptService, 33.33, 3)).isCloseTo(9.999, org.assertj.core.data.Offset.offset(0.01));
    }
}
