package com.huatai.careeragent.agent.tool;

import com.huatai.careeragent.report.FinalReport;
import com.huatai.careeragent.report.FinalReportRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetFinalReportToolTest {
    private final FinalReportRepository repository = mock(FinalReportRepository.class);
    private final GetFinalReportTool tool = new GetFinalReportTool(repository);

    @Test
    void returnsOnlyReportBoundToContextUserAndTask() {
        FinalReport report = new FinalReport(7L, 11L, 2L, 3L, 1, Map.of("status", "COMPLETE"));
        when(repository.findFirstByUserIdAndTaskIdOrderByVersionDescIdDesc(7L, 11L))
                .thenReturn(Optional.of(report));

        GetFinalReportTool.Output output = tool.execute(new GetFinalReportTool.Input(11L),
                new ToolExecutionContext(7L, 11L, "trace", AgentNames.LEARNING_PLAN_AGENT));

        assertThat(output.taskId()).isEqualTo(11L);
        assertThat(output.report()).containsEntry("status", "COMPLETE");
        verify(repository).findFirstByUserIdAndTaskIdOrderByVersionDescIdDesc(7L, 11L);
    }

    @Test
    void rejectsCrossTaskInputBeforeQuerying() {
        assertThatThrownBy(() -> tool.execute(new GetFinalReportTool.Input(12L),
                new ToolExecutionContext(7L, 11L, "trace", AgentNames.LEARNING_PLAN_AGENT)))
                .isInstanceOf(ToolException.class).hasMessageContaining("does not match");
    }
}
