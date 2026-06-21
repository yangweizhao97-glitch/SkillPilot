package com.huatai.careeragent.agent.tool;

import com.huatai.careeragent.report.FinalReport;
import com.huatai.careeragent.report.FinalReportRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

@Component
public class GetFinalReportTool implements Tool<GetFinalReportTool.Input, GetFinalReportTool.Output> {
    public static final String NAME = "getFinalReport";
    private final FinalReportRepository repository;

    public GetFinalReportTool(FinalReportRepository repository) {
        this.repository = repository;
    }

    @Override public String name() { return NAME; }
    @Override public Class<Input> inputType() { return Input.class; }
    @Override public Set<String> allowedAgents() { return Set.of(AgentNames.LEARNING_PLAN_AGENT); }

    @Override
    @Transactional(readOnly = true)
    public Output execute(Input input, ToolExecutionContext context) {
        if (!input.taskId().equals(context.taskId())) {
            throw new ToolException("TASK_CONTEXT_MISMATCH", "Report task does not match agent context", false);
        }
        FinalReport report = repository.findFirstByUserIdAndTaskIdOrderByVersionDescIdDesc(
                        context.userId(), input.taskId())
                .orElseThrow(() -> new ToolException("FINAL_REPORT_NOT_FOUND", "Final report not found", false));
        return new Output(report.getId(), report.getTaskId(), report.getReportJson());
    }

    public record Input(@NotNull Long taskId) { }
    public record Output(Long reportId, Long taskId, Map<String, Object> report) { }
}
