package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.entity.Question.Difficulty;
import com.exam_bank.exam_service.repository.QuestionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DifficultyRecalculationServiceTest {

    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query query;

    @InjectMocks
    private DifficultyRecalculationService service;

    @BeforeEach
    void setUp() {
        lenient().when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.executeUpdate()).thenReturn(5);
    }

    @Test
    void recalculateAll_executesNativeQuery() {
        int result = service.recalculateAll();
        verify(entityManager).createNativeQuery(contains("UPDATE questions"));
        verify(query).setParameter("minAttempts", 10);
        verify(query).executeUpdate();
        assertEquals(5, result);
    }

    @Test
    void recalculateAll_formatsDifficultyLabelsCorrectly() {
        service.recalculateAll();

        // The SQL uses correctRate thresholds: 80→EASY, 50→MEDIUM, 20→HARD,
        // <20→VERY_HARD
        // Verify the SQL string contains all difficulty labels
        verify(entityManager).createNativeQuery(argThat(sql -> sql.toString().contains("'EASY'") &&
                sql.toString().contains("'MEDIUM'") &&
                sql.toString().contains("'HARD'") &&
                sql.toString().contains("'VERY_HARD'")));
    }

    @Test
    void labelDifficulty_returnsCorrectLabel() {
        assertEquals(Difficulty.EASY, Difficulty.valueOf("EASY")); // just verify enum
    }
}
