package com.huatai.careeragent.agent.workflow;

import com.huatai.careeragent.task.WorkflowStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class InterviewQuestionVerifier implements WorkflowStepVerifier {
    private static final int MIN_QUESTION_COUNT = 5;
    private static final int MIN_DIVERSITY = 2;

    @Override
    public WorkflowStatus supports() {
        return WorkflowStatus.GENERATING_QUESTIONS;
    }

    @Override
    public VerificationResult verify(Long taskId, WorkflowStepResult result) {
        Map<String, Object> metrics = result.qualitySignals();
        int questionCount = number(metrics.get("questionCount"));
        int questionTypeCount = number(metrics.get("questionTypeCount"));
        int difficultyCount = number(metrics.get("difficultyCount"));
        double expectedPointsCoverage = ratio(metrics.get("expectedPointsCoverage"));
        double evidenceCoverage = ratio(metrics.get("evidenceCoverage"));
        boolean passed = questionCount >= MIN_QUESTION_COUNT
                && questionTypeCount >= MIN_DIVERSITY
                && difficultyCount >= MIN_DIVERSITY
                && expectedPointsCoverage >= 1.0
                && evidenceCoverage >= 0.6;
        if (passed) {
            return VerificationResult.passed("Interview questions passed quality checks", metrics);
        }
        return VerificationResult.failed(NextAction.RETRY_STEP, "Interview questions did not meet quality thresholds",
                metrics);
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private double ratio(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }
}
