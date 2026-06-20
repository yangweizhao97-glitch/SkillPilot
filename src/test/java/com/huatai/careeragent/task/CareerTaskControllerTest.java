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
import com.huatai.careeragent.task.log.AgentExecutionLogRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
                .andExpect(jsonPath("$.data.items.length()").value(7))
                .andExpect(jsonPath("$.data.items[0].workflowStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.items[0].progress").value(0))
                .andExpect(jsonPath("$.data.items[6].workflowStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[6].progress").value(100))
                .andExpect(jsonPath("$.data.items[6].traceId").value(completed.getTraceId()))
                .andExpect(jsonPath("$.data.items[6].updatedAt").isNotEmpty());

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
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.items[4].workflowStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.items[4].status").value("FAILED"))
                .andExpect(jsonPath("$.data.items[4].errorMessage").value("matching provider unavailable"));
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
