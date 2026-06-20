package com.huatai.careeragent.agent.tool;

import com.huatai.careeragent.document.Document;
import com.huatai.careeragent.document.DocumentRepository;
import com.huatai.careeragent.file.FileType;
import com.huatai.careeragent.job.Job;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.knowledge.retrieval.RetrievalMode;
import com.huatai.careeragent.resume.Resume;
import com.huatai.careeragent.resume.ResumeRepository;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import com.huatai.careeragent.task.WorkflowStatus;
import com.huatai.careeragent.user.User;
import com.huatai.careeragent.user.UserRepository;
import com.huatai.careeragent.user.UserRole;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ToolExecutorIntegrationTest {
    @Autowired
    private ToolExecutor toolExecutor;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ToolCallLogRepository toolCallLogRepository;

    @Autowired
    private AgentTaskRepository agentTaskRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        toolCallLogRepository.deleteAll();
        agentTaskRepository.deleteAll();
        jobRepository.deleteAll();
        resumeRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void executesMvpToolsAndRecordsSuccessLogs() {
        Resources resources = createResources("owner");
        ToolExecutionContext context = context(resources, AgentNames.JOB_MATCH_AGENT);

        ToolResponse<?> resumeResponse = toolExecutor.execute(new ToolRequest<>(
                GetResumeTool.NAME, new GetResumeTool.Input(resources.resume().getId()), context
        ));
        ToolResponse<?> jobResponse = toolExecutor.execute(new ToolRequest<>(
                GetJobDescriptionTool.NAME, new GetJobDescriptionTool.Input(resources.job().getId()), context
        ));
        ToolResponse<?> searchResponse = toolExecutor.execute(new ToolRequest<>(
                SearchUserKnowledgeBaseTool.NAME,
                new SearchUserKnowledgeBaseTool.Input("not-present", List.of(), 5, RetrievalMode.KEYWORD),
                context
        ));

        assertThat(resumeResponse.success()).isTrue();
        assertThat((GetResumeTool.Output) resumeResponse.output())
                .extracting(GetResumeTool.Output::content)
                .isEqualTo("Java Spring backend resume");
        assertThat(jobResponse.success()).isTrue();
        assertThat((GetJobDescriptionTool.Output) jobResponse.output())
                .extracting(GetJobDescriptionTool.Output::description)
                .isEqualTo("Build Spring services");
        assertThat(searchResponse.success()).isTrue();
        assertThat(((SearchUserKnowledgeBaseTool.Output) searchResponse.output()).items()).isEmpty();

        List<ToolCallLog> logs = toolCallLogRepository.findByTaskIdOrderByCreatedAtAscIdAsc(resources.task().getId());
        assertThat(logs).hasSize(3).allMatch(log -> log.getStatus() == ToolCallStatus.SUCCESS);
        assertThat(logs).extracting(ToolCallLog::getTraceId).containsOnly(resources.task().getTraceId());
    }

    @Test
    void rejectsCrossUserResourceAndTaskAccessAndRecordsFailures() {
        Resources owner = createResources("owner");
        Resources other = createResources("other");

        ToolResponse<?> resourceDenied = toolExecutor.execute(new ToolRequest<>(
                GetResumeTool.NAME,
                new GetResumeTool.Input(other.resume().getId()),
                context(owner, AgentNames.JOB_MATCH_AGENT)
        ));
        ToolExecutionContext crossUserContext = new ToolExecutionContext(
                other.user().getId(), owner.task().getId(), owner.task().getTraceId(), AgentNames.JOB_MATCH_AGENT
        );
        ToolResponse<?> taskDenied = toolExecutor.execute(new ToolRequest<>(
                GetResumeTool.NAME,
                new GetResumeTool.Input(owner.resume().getId()),
                crossUserContext
        ));

        assertThat(resourceDenied.success()).isFalse();
        assertThat(resourceDenied.error().code()).isEqualTo("RESUME_NOT_FOUND");
        assertThat(taskDenied.success()).isFalse();
        assertThat(taskDenied.error().code()).isEqualTo("TOOL_TASK_ACCESS_DENIED");
        assertThat(toolCallLogRepository.findAll())
                .hasSize(2)
                .allMatch(log -> log.getStatus() == ToolCallStatus.FAILED);
    }

    @Test
    void validatesInputAgentWhitelistAndRedactsSensitiveAuditFields() {
        Resources resources = createResources("audit");
        ToolResponse<?> invalidInput = toolExecutor.execute(new ToolRequest<>(
                SearchUserKnowledgeBaseTool.NAME,
                new SearchUserKnowledgeBaseTool.Input("", List.of(), 5, RetrievalMode.KEYWORD),
                context(resources, AgentNames.JOB_MATCH_AGENT)
        ));
        ToolResponse<?> agentDenied = toolExecutor.execute(new ToolRequest<>(
                GetJobDescriptionTool.NAME,
                new GetJobDescriptionTool.Input(resources.job().getId()),
                context(resources, AgentNames.RESUME_ANALYSIS_AGENT)
        ));

        toolRegistry.register(new SensitiveEchoTool());
        ToolResponse<?> sensitive = toolExecutor.execute(new ToolRequest<>(
                SensitiveEchoTool.NAME,
                new SensitiveInput("visible", "jwt-value"),
                context(resources, AgentNames.JOB_MATCH_AGENT)
        ));

        assertThat(invalidInput.error().code()).isEqualTo("INVALID_TOOL_REQUEST");
        assertThat(agentDenied.error().code()).isEqualTo("TOOL_AGENT_NOT_ALLOWED");
        assertThat(sensitive.success()).isTrue();
        ToolCallLog sensitiveLog = toolCallLogRepository.findAll().stream()
                .filter(log -> log.getToolName().equals(SensitiveEchoTool.NAME))
                .findFirst()
                .orElseThrow();
        assertThat(sensitiveLog.getInput()).containsEntry("passwordToken", "***");
        assertThat(sensitiveLog.getInput()).containsEntry("query", "visible");
        assertThat(sensitiveLog.getOutput()).containsEntry("authorization", "***");
    }

    private Resources createResources(String prefix) {
        User user = userRepository.save(new User(
                prefix + "-" + UUID.randomUUID() + "@example.com", "hash", prefix, UserRole.USER
        ));
        Document document = documentRepository.save(new Document(
                user.getId(), null, FileType.RESUME, "resume.txt", "Java Spring backend resume", Map.of()
        ));
        Resume resume = resumeRepository.save(new Resume(user.getId(), document.getId(), "Backend Resume"));
        Job job = jobRepository.save(new Job(
                user.getId(), null, "Example Inc", "Backend Engineer", "Build Spring services"
        ));
        AgentTask task = agentTaskRepository.save(new AgentTask(
                user.getId(), "trace_" + UUID.randomUUID().toString().replace("-", ""),
                resume.getId(), job.getId(), List.of(WorkflowStatus.MATCHING_JOB)
        ));
        return new Resources(user, resume, job, task);
    }

    private ToolExecutionContext context(Resources resources, String agentName) {
        return new ToolExecutionContext(
                resources.user().getId(), resources.task().getId(), resources.task().getTraceId(), agentName
        );
    }

    private record Resources(User user, Resume resume, Job job, AgentTask task) {
    }

    private record SensitiveInput(@NotBlank String query, String passwordToken) {
    }

    private record SensitiveOutput(String authorization) {
    }

    private static final class SensitiveEchoTool implements Tool<SensitiveInput, SensitiveOutput> {
        private static final String NAME = "sensitiveEchoTest";

        @Override
        public String name() {
            return NAME;
        }

        @Override
        public Class<SensitiveInput> inputType() {
            return SensitiveInput.class;
        }

        @Override
        public Set<String> allowedAgents() {
            return Set.of(AgentNames.JOB_MATCH_AGENT);
        }

        @Override
        public SensitiveOutput execute(SensitiveInput input, ToolExecutionContext context) {
            return new SensitiveOutput("Bearer should-not-be-logged");
        }
    }
}
