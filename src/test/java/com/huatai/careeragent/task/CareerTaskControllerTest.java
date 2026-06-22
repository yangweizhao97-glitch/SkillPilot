package com.huatai.careeragent.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.document.Document;
import com.huatai.careeragent.document.DocumentRepository;
import com.huatai.careeragent.file.FileType;
import com.huatai.careeragent.job.Job;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.resume.Resume;
import com.huatai.careeragent.resume.ResumeRepository;
import com.huatai.careeragent.task.log.AgentExecutionLog;
import com.huatai.careeragent.task.log.AgentExecutionLogRepository;
import com.huatai.careeragent.task.log.ExecutionLogStatus;
import com.huatai.careeragent.user.User;
import com.huatai.careeragent.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
class CareerTaskControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgentTaskRepository agentTaskRepository;

    @Autowired
    private AgentExecutionLogRepository executionLogRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private CareerWorkflowStepHandler stepHandler;

    @AfterEach
    void cleanUp() {
        awaitNoRunningTasks();
        executionLogRepository.deleteAll();
        agentTaskRepository.deleteAll();
        jobRepository.deleteAll();
        resumeRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createsAndPollsTaskUntilSuccessWithUserIsolation() throws Exception {
        AuthenticatedResources owner = createAuthenticatedResources();
        String otherToken = registerAndLogin().token();

        String response = mockMvc.perform(post("/api/career-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": %d,
                                  "jobId": %d,
                                  "enabledSteps": ["MATCHING_JOB", "ANALYZING_RESUME", "GENERATING_QUESTIONS"]
                                }
                                """.formatted(owner.resumeId(), owner.jobId()))
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.progress").value(0))
                .andExpect(jsonPath("$.data.traceId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long taskId = objectMapper.readTree(response).path("data").path("taskId").asLong();

        AgentTask completed = awaitStatus(taskId, WorkflowStatus.SUCCESS);
        assertThat(completed.getProgress()).isEqualTo(100);
        assertThat(completed.getStartedAt()).isNotNull();
        assertThat(completed.getFinishedAt()).isNotNull();

        mockMvc.perform(get("/api/career-tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.progress").value(100))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/career-tasks/{taskId}/logs", taskId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.traceId").value(completed.getTraceId()))
                .andExpect(jsonPath("$.data.steps.length()").value(7))
                .andExpect(jsonPath("$.data.steps[0].step").value("READING_INPUTS"))
                .andExpect(jsonPath("$.data.steps[0].title").value("读取简历和岗位描述"))
                .andExpect(jsonPath("$.data.steps[6].step").value("FINAL_REPORT"))
                .andExpect(jsonPath("$.data.steps[6].summary").value("报告已生成"))
                .andExpect(jsonPath("$.data.steps[?(@.status == 'RUNNING')]").value(hasSize(0)))
                .andExpect(jsonPath("$.data.items.length()").value(13))
                .andExpect(jsonPath("$.data.items[?(@.status == 'HANDOFF_COMPLETED')]").value(hasSize(3)))
                .andExpect(jsonPath("$.data.items[0].workflowStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].progress").value(0))
                .andExpect(jsonPath("$.data.items[12].workflowStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[12].progress").value(100))
                .andExpect(jsonPath("$.data.items[12].traceId").value(completed.getTraceId()))
                .andExpect(jsonPath("$.data.items[12].updatedAt").isNotEmpty());

        mockMvc.perform(get("/api/career-tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CAREER_TASK_NOT_FOUND"));

        mockMvc.perform(get("/api/career-tasks/{taskId}/logs", taskId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CAREER_TASK_NOT_FOUND"));
    }

    @Test
    void recordsAsynchronousWorkflowFailure() throws Exception {
        AuthenticatedResources owner = createAuthenticatedResources();
        doThrow(new IllegalStateException("matching provider unavailable"))
                .when(stepHandler).execute(org.mockito.ArgumentMatchers.anyLong(), eq(WorkflowStatus.MATCHING_JOB));

        String response = mockMvc.perform(post("/api/career-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": %d,
                                  "jobId": %d
                                }
                                """.formatted(owner.resumeId(), owner.jobId()))
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long taskId = objectMapper.readTree(response).path("data").path("taskId").asLong();

        AgentTask failed = awaitStatus(taskId, WorkflowStatus.FAILED);
        assertThat(failed.getErrorMessage()).contains("matching provider unavailable");
        assertThat(failed.getFinishedAt()).isNotNull();

        mockMvc.perform(get("/api/career-tasks/{taskId}/logs", taskId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[2].workflowStatus").value("MATCHING_JOB"))
                .andExpect(jsonPath("$.data.items[2].status").value("STEP_FAILED"))
                .andExpect(jsonPath("$.data.items[2].errorMessage").value("matching provider unavailable"))
                .andExpect(jsonPath("$.data.steps[?(@.status == 'FAILED')]").value(hasSize(1)))
                .andExpect(jsonPath("$.data.steps[0].type").value("TASK_FAILED"));
    }

    @Test
    void progressToleratesLearningPlanAgentLogs() throws Exception {
        AuthenticatedResources owner = createAuthenticatedResources();

        String response = mockMvc.perform(post("/api/career-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": %d,
                                  "jobId": %d
                                }
                                """.formatted(owner.resumeId(), owner.jobId()))
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long taskId = objectMapper.readTree(response).path("data").path("taskId").asLong();
        AgentTask completed = awaitStatus(taskId, WorkflowStatus.SUCCESS);

        executionLogRepository.save(AgentExecutionLog.agentExecution(
                completed.getUserId(), completed.getId(), completed.getTraceId(),
                "LEARNING_PLAN_AGENT", "GENERATING_LEARNING_PLAN", "taskId=" + taskId,
                "learningPlanId=1", ExecutionLogStatus.STEP_COMPLETED, 1200,
                com.huatai.careeragent.llm.LlmResponse.TokenUsage.empty(), null
        ));

        mockMvc.perform(get("/api/career-tasks/{taskId}/progress", taskId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.taskId").value(taskId))
                .andExpect(jsonPath("$.data.steps[?(@.step == 'FINAL_REPORT')]").value(hasSize(1)))
                .andExpect(jsonPath("$.data.technicalDetails[?(@.label == '学习计划生成')]").value(hasSize(1)));
    }

    @Test
    void rejectsResourcesOwnedByAnotherUserAndInvalidSteps() throws Exception {
        AuthenticatedResources owner = createAuthenticatedResources();
        String otherToken = registerAndLogin().token();

        mockMvc.perform(post("/api/career-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": %d,
                                  "jobId": %d
                                }
                                """.formatted(owner.resumeId(), owner.jobId()))
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESUME_NOT_FOUND"));

        mockMvc.perform(post("/api/career-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": %d,
                                  "jobId": %d,
                                  "enabledSteps": ["SUCCESS"]
                                }
                                """.formatted(owner.resumeId(), owner.jobId()))
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_ENABLED_STEP"));
    }

    @Test
    void streamsPersistedTaskEventsAndResynchronizesAfterReconnect() throws Exception {
        AuthenticatedResources owner = createAuthenticatedResources();
        String otherToken = registerAndLogin().token();
        doAnswer(invocation -> {
            Thread.sleep(80);
            return null;
        }).when(stepHandler).execute(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(WorkflowStatus.class));

        String response = mockMvc.perform(post("/api/career-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resumeId":%d,"jobId":%d}
                                """.formatted(owner.resumeId(), owner.jobId()))
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long taskId = objectMapper.readTree(response).path("data").path("taskId").asLong();

        var stream = mockMvc.perform(get("/api/career-tasks/{taskId}/events", taskId)
                        .header("Authorization", "Bearer " + owner.token())
                        .header("Last-Event-ID", "step-previous"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:TASK_SNAPSHOT")))
                .andExpect(content().string(containsString("\"resumedAfterEventId\":\"step-previous\"")))
                .andExpect(content().string(containsString("event:USER_STEP_EVENT")))
                .andExpect(content().string(containsString("event:TASK_STREAM_COMPLETED")));

        mockMvc.perform(get("/api/career-tasks/{taskId}/events", taskId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CAREER_TASK_NOT_FOUND"));
    }

    private AuthenticatedResources createAuthenticatedResources() throws Exception {
        AuthenticatedUser authenticatedUser = registerAndLogin();
        User user = userRepository.findByEmail(authenticatedUser.email()).orElseThrow();
        Document document = documentRepository.save(new Document(
                user.getId(), null, FileType.RESUME, "resume.txt", "Java backend resume", java.util.Map.of()
        ));
        Resume resume = resumeRepository.save(new Resume(user.getId(), document.getId(), "Java Resume"));
        Job job = jobRepository.save(new Job(user.getId(), null, "Example Inc", "Backend Engineer", "Spring Boot JD"));
        return new AuthenticatedResources(authenticatedUser.token(), resume.getId(), job.getId());
    }

    private AuthenticatedUser registerAndLogin() throws Exception {
        String email = "task-user-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123","nickname":"Task User"}
                                """.formatted(email)))
                .andExpect(status().isOk());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(response);
        return new AuthenticatedUser(email, root.path("data").path("accessToken").asText());
    }

    private AgentTask awaitStatus(Long taskId, WorkflowStatus expected) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            AgentTask task = agentTaskRepository.findById(taskId).orElseThrow();
            if (task.getStatus() == expected) {
                return task;
            }
            Thread.sleep(20);
        }
        AgentTask task = agentTaskRepository.findById(taskId).orElseThrow();
        throw new AssertionError("Expected status " + expected + " but was " + task.getStatus());
    }

    private void awaitNoRunningTasks() {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            boolean running = agentTaskRepository.findAll().stream().anyMatch(task -> !task.getStatus().isTerminal());
            if (!running) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private record AuthenticatedUser(String email, String token) {
    }

    private record AuthenticatedResources(String token, Long resumeId, Long jobId) {
    }
}
