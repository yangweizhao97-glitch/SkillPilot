package com.huatai.careeragent.conversation;

import com.huatai.careeragent.conversation.CareerAgentDtos.AgentNextAction;
import com.huatai.careeragent.conversation.CareerAgentDtos.CareerIntent;
import com.huatai.careeragent.conversation.CareerAgentDtos.IntentRequest;
import com.huatai.careeragent.conversation.CareerAgentDtos.RequiredResource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CareerIntentServiceTest {
    private final CareerIntentService service = new CareerIntentService();

    @Test
    void classifiesJobMatchAndRequestsMissingResources() {
        var response = service.classify(new IntentRequest(
                "帮我看看这份简历适不适合 Java 后端岗位", false, true, false
        ));

        assertThat(response.intent()).isEqualTo(CareerIntent.JOB_MATCH);
        assertThat(response.needsWorkflow()).isTrue();
        assertThat(response.requiredResources()).containsExactly(RequiredResource.RESUME, RequiredResource.JOB);
        assertThat(response.missingResources()).containsExactly(RequiredResource.RESUME);
        assertThat(response.nextAction()).isEqualTo(AgentNextAction.ASK_USER);
    }

    @Test
    void startsWorkflowWhenCareerAnalysisResourcesAreReady() {
        var response = service.classify(new IntentRequest(
                "生成一份完整职业准备报告", true, true, false
        ));

        assertThat(response.intent()).isEqualTo(CareerIntent.CAREER_ANALYSIS);
        assertThat(response.missingResources()).isEmpty();
        assertThat(response.nextAction()).isEqualTo(AgentNextAction.START_WORKFLOW);
    }

    @Test
    void routesLearningPlanToReportContext() {
        var response = service.classify(new IntentRequest(
                "基于报告生成学习计划", true, true, true
        ));

        assertThat(response.intent()).isEqualTo(CareerIntent.LEARNING_PLAN);
        assertThat(response.needsWorkflow()).isFalse();
        assertThat(response.requiredResources()).containsExactly(RequiredResource.REPORT);
        assertThat(response.nextAction()).isEqualTo(AgentNextAction.GENERATE_LEARNING_PLAN);
    }

    @Test
    void answersGeneralAdviceWithoutForcingResources() {
        var response = service.classify(new IntentRequest(
                "Java 后端面试一般怎么准备？先给我通用建议", false, false, false
        ));

        assertThat(response.intent()).isEqualTo(CareerIntent.GENERAL_CAREER_QA);
        assertThat(response.needsWorkflow()).isFalse();
        assertThat(response.requiredResources()).isEmpty();
        assertThat(response.missingResources()).isEmpty();
        assertThat(response.nextAction()).isEqualTo(AgentNextAction.ANSWER_DIRECTLY);
    }
}
