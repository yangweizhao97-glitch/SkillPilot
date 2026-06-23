package com.huatai.careeragent.agent.context;

public record InterviewQuestionContextInput(
        Long resumeId,
        Long jobId,
        String agentName
) {
}
