package com.huatai.careeragent.agent.tool;

import com.huatai.careeragent.job.Job;
import com.huatai.careeragent.job.JobRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
public class GetJobDescriptionTool implements Tool<GetJobDescriptionTool.Input, GetJobDescriptionTool.Output> {
    public static final String NAME = "getJobDescription";

    private final JobRepository jobRepository;

    public GetJobDescriptionTool(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public Set<String> allowedAgents() {
        return Set.of(AgentNames.JOB_MATCH_AGENT, AgentNames.INTERVIEW_QUESTION_AGENT);
    }

    @Override
    @Transactional(readOnly = true)
    public Output execute(Input input, ToolExecutionContext context) {
        Job job = jobRepository.findByIdAndUserId(input.jobId(), context.userId())
                .orElseThrow(() -> new ToolException("JOB_NOT_FOUND", "Job not found", false));
        return new Output(job.getId(), job.getCompany(), job.getPosition(), job.getJdText());
    }

    public record Input(@NotNull Long jobId) {
    }

    public record Output(Long jobId, String company, String position, String description) {
    }
}
