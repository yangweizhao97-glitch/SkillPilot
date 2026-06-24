package com.huatai.careeragent.conversation;

import com.huatai.careeragent.conversation.CareerAgentDtos.IntentRequest;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CareerProfileServiceTest {
    private final CareerProfileRepository repository = mock(CareerProfileRepository.class);
    private final CareerProfileService service = new CareerProfileService(repository);
    private final CareerIntentService intentService = new CareerIntentService();

    @Test
    void remembersTargetRoleStageAndWeaknessTags() {
        when(repository.findByUserId(7L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var intent = intentService.classify(new IntentRequest(
                "秋招 Java 后端，系统设计和英文面试比较薄弱", true, true, false
        ));

        var response = service.observe(7L, "秋招 Java 后端，系统设计和英文面试比较薄弱", intent);

        assertThat(response.targetRoles()).contains("Java 后端", "外企岗位");
        assertThat(response.careerStages()).contains("秋招");
        assertThat(response.weaknessTags()).contains("系统设计", "英文面试");
        assertThat(response.summary()).contains("目标：Java 后端");
        assertThat(response.suggestedPrompts()).contains("针对系统设计出 5 道练习题");
        verify(repository).save(any(CareerProfile.class));
    }
}
