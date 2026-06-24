package com.huatai.careeragent.conversation;

import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.conversation.CareerAgentDtos.AgentMessageRole;
import com.huatai.careeragent.conversation.CareerAgentDtos.AgentMessageType;
import com.huatai.careeragent.conversation.CareerAgentDtos.AppendAgentMessageRequest;
import com.huatai.careeragent.report.FinalReportRepository;
import com.huatai.careeragent.task.AgentTaskRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CareerAgentConversationServiceTest {
    private final CareerAgentConversationRepository conversations = mock(CareerAgentConversationRepository.class);
    private final CareerAgentMessageRepository messages = mock(CareerAgentMessageRepository.class);
    private final CareerProfileService profiles = mock(CareerProfileService.class);
    private final AgentTaskRepository tasks = mock(AgentTaskRepository.class);
    private final FinalReportRepository reports = mock(FinalReportRepository.class);
    private final CareerAgentConversationService service = new CareerAgentConversationService(
            conversations, messages, profiles, tasks, reports
    );

    @Test
    void rejectsAppendingMessageWithForeignTaskReference() {
        when(tasks.findByIdAndUserId(99L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.append(7L, new AppendAgentMessageRequest(
                AgentMessageRole.SYSTEM,
                AgentMessageType.WORKFLOW_STATUS,
                "任务进度",
                99L,
                null,
                Map.of()
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("Career task not found");
    }

    @Test
    void rejectsAppendingMessageWithForeignReportReference() {
        when(reports.findByIdAndUserId(66L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.append(7L, new AppendAgentMessageRequest(
                AgentMessageRole.SYSTEM,
                AgentMessageType.TOOL_STATUS,
                "报告状态",
                null,
                66L,
                Map.of()
        ))).isInstanceOf(BusinessException.class)
                .hasMessage("Report not found");
    }
}
