package com.exam_bank.exam_service.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DifficultyRecalculationService Unit Tests")
class DifficultyRecalculationServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @Test
    @DisplayName("recalculateAll executes native update with minAttempts=10")
    void recalculateAllExecutesNativeUpdate() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(8);

        DifficultyRecalculationService service = new DifficultyRecalculationService(entityManager);
        int updated = service.recalculateAll();

        assertThat(updated).isEqualTo(8);
        verify(entityManager).createNativeQuery(anyString());
        verify(query).setParameter("minAttempts", 10);
        verify(query).executeUpdate();
    }

    @Test
    @DisplayName("recalculateSingle sets questionId and minAttempts parameters")
    void recalculateSingleSetsParameters() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.executeUpdate()).thenReturn(1);

        DifficultyRecalculationService service = new DifficultyRecalculationService(entityManager);
        service.recalculateSingle(55L);

        verify(query).setParameter("questionId", 55L);
        verify(query).setParameter("minAttempts", 10);
        verify(query).executeUpdate();
    }

    @Test
    @DisplayName("scheduledRecalculate delegates to recalculateAll")
    void scheduledRecalculateDelegatesToRecalculateAll() {
        DifficultyRecalculationService service = spy(new DifficultyRecalculationService(entityManager));
        doReturn(4).when(service).recalculateAll();

        service.scheduledRecalculate();

        verify(service).recalculateAll();
    }
}
