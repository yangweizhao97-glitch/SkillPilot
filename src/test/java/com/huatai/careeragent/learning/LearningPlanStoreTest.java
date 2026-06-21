package com.huatai.careeragent.learning;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningPlanStoreTest {
    private final LearningPlanRepository repository = mock(LearningPlanRepository.class);
    private final LearningPlanStore store = new LearningPlanStore(repository, new ObjectMapper());

    @Test
    void reclaimsAStaleGenerationWithANewToken() {
        LearningPlan plan = mock(LearningPlan.class);
        when(plan.getGenerationStatus()).thenReturn("GENERATING");
        when(plan.generationIsStale(any())).thenReturn(true);
        when(repository.findLockedByUserIdAndTaskId(3L, 9L)).thenReturn(Optional.of(plan));

        String generationId = store.claim(3L, 9L, 12L);

        assertThat(generationId).isNotBlank();
        verify(plan).restart(12L, generationId);
    }

    @Test
    void preservesAnActiveGenerationClaim() {
        LearningPlan plan = mock(LearningPlan.class);
        when(plan.getGenerationStatus()).thenReturn("GENERATING");
        when(plan.generationIsStale(any())).thenReturn(false);
        when(repository.findLockedByUserIdAndTaskId(3L, 9L)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> store.claim(3L, 9L, 12L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already running");
        verify(plan, never()).restart(any(), any());
    }
}
