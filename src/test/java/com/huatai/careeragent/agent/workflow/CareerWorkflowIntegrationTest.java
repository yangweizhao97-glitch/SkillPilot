package com.huatai.careeragent.agent.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.agent.tool.ToolCallLogRepository;
import com.huatai.careeragent.document.Document;
import com.huatai.careeragent.document.DocumentRepository;
import com.huatai.careeragent.file.FileType;
import com.huatai.careeragent.file.UploadedFileRepository;
import com.huatai.careeragent.knowledge.chunk.DocumentChunkRepository;
import com.huatai.careeragent.interview.InterviewQuestionRepository;
import com.huatai.careeragent.job.Job;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmResponse;
import com.huatai.careeragent.report.JobMatchReportRepository;
import com.huatai.careeragent.report.FinalReportRepository;
import com.huatai.careeragent.report.ResumeAnalysisReportRepository;
import com.huatai.careeragent.resume.Resume;
import com.huatai.careeragent.resume.ResumeRepository;
import com.huatai.careeragent.task.AgentTask;
import com.huatai.careeragent.task.AgentTaskRepository;
import com.huatai.careeragent.task.WorkflowStatus;
import com.huatai.careeragent.task.log.AgentExecutionLogRepository;
import com.huatai.careeragent.user.User;
import com.huatai.careeragent.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "career-agent.agent.initial-retry-delay=0ms",
        "career-agent.storage.upload-dir=target/workflow-test-uploads",
        "career-agent.chunking.target-tokens=20",
        "career-agent.chunking.overlap-tokens=4"
})
@AutoConfigureMockMvc
class CareerWorkflowIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AgentTaskRepository taskRepository;
    @Autowired private AgentExecutionLogRepository executionLogRepository;
    @Autowired private ToolCallLogRepository toolCallLogRepository;
    @Autowired private JobMatchReportRepository jobMatchReportRepository;
    @Autowired private ResumeAnalysisReportRepository resumeAnalysisReportRepository;
    @Autowired private InterviewQuestionRepository interviewQuestionRepository;
    @Autowired private FinalReportRepository finalReportRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentChunkRepository documentChunkRepository;
    @Autowired private UploadedFileRepository uploadedFileRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean
    private LlmClient llmClient;

    @AfterEach
    void cleanUp() throws Exception {
        awaitNoRunningTasks();
        toolCallLogRepository.deleteAll();
        executionLogRepository.deleteAll();
        finalReportRepository.deleteAll();
        interviewQuestionRepository.deleteAll();
        jobMatchReportRepository.deleteAll();
        resumeAnalysisReportRepository.deleteAll();
        taskRepository.deleteAll();
        jobRepository.deleteAll();
        resumeRepository.deleteAll();
        documentChunkRepository.deleteAll();
        documentRepository.deleteAll();
        uploadedFileRepository.deleteAll();
        userRepository.deleteAll();
        org.springframework.util.FileSystemUtils.deleteRecursively(Path.of("target/workflow-test-uploads"));
    }

    @Test
    void runsCompleteWorkflowRepairsSchemaPersistsVersionsAndEnforcesIsolation() throws Exception {
        Resources owner = createResources();
        String otherToken = registerAndLogin().token();
        when(llmClient.complete(any()))
                .thenReturn(response("{\"matchScore\":82}"))
                .thenReturn(response(jobMatchJson()))
                .thenReturn(response(resumeAnalysisJson()))
                .thenReturn(response(interviewQuestionsJson()))
                .thenReturn(response(jobMatchJson()))
                .thenReturn(response(resumeAnalysisJson()))
                .thenReturn(response(interviewQuestionsJson()));

        Long fullTaskId = createTask(owner.token(), owner.resumeId(), owner.jobId(), null);
        AgentTask completed = awaitStatus(fullTaskId, WorkflowStatus.SUCCESS);

        assertThat(jobMatchReportRepository.findAll()).hasSize(1);
        assertThat(resumeAnalysisReportRepository.findAll()).hasSize(1);
        assertThat(interviewQuestionRepository.findAll()).hasSize(2);
        assertThat(finalReportRepository.findAll()).hasSize(1);
        assertThat(toolCallLogRepository.findByTaskIdOrderByCreatedAtAscIdAsc(fullTaskId)).hasSize(9);
        assertThat(executionLogRepository.findByTaskIdAndUserIdOrderByCreatedAtAscIdAsc(fullTaskId, completed.getUserId()))
                .extracting(log -> log.getAgentName())
                .contains("JOB_MATCH_AGENT", "RESUME_ANALYSIS_AGENT", "INTERVIEW_QUESTION_AGENT");

        String reportId = finalReportRepository.findAll().getFirst().getId().toString();
        mockMvc.perform(get("/api/reports").header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("COMPLETE"))
                .andExpect(jsonPath("$.data[0].version").value(1));
        mockMvc.perform(get("/api/reports/{reportId}", reportId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.report.jobMatch.version").value(1))
                .andExpect(jsonPath("$.data.report.resumeAnalysis.version").value(1))
                .andExpect(jsonPath("$.data.report.interviewQuestions.count").value(2));
        mockMvc.perform(get("/api/reports/{reportId}", reportId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REPORT_NOT_FOUND"));

        mockMvc.perform(get("/api/jobs/{jobId}/match-reports", owner.jobId())
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].result.matchScore").value(82));
        mockMvc.perform(get("/api/resumes/{resumeId}/analysis-reports", owner.resumeId())
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].result.summary").value("Strong backend profile"));
        mockMvc.perform(get("/api/interview/questions")
                        .param("resumeId", owner.resumeId().toString())
                        .param("jobId", owner.jobId().toString())
                        .param("difficulty", "HARD")
                        .param("questionType", "SYSTEM_DESIGN")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].noCitationReason").value("General system design question"));

        Long matchTaskId = createMatchTask(owner);
        awaitStatus(matchTaskId, WorkflowStatus.SUCCESS);
        assertThat(jobMatchReportRepository.findAll())
                .extracting(report -> report.getVersion())
                .containsExactlyInAnyOrder(1, 2);

        awaitStatus(createAnalysisTask(owner), WorkflowStatus.SUCCESS);
        assertThat(resumeAnalysisReportRepository.findAll())
                .extracting(report -> report.getVersion())
                .containsExactlyInAnyOrder(1, 2);

        awaitStatus(createQuestionTask(owner), WorkflowStatus.SUCCESS);
        assertThat(interviewQuestionRepository.findAll()).hasSize(4);

        mockMvc.perform(get("/api/jobs/{jobId}/match-reports", owner.jobId())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));
        mockMvc.perform(get("/api/interview/questions")
                        .param("jobId", owner.jobId().toString())
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));
    }

    @Test
    void refreshCreatesPartialVersionWhenSectionsAreMissing() throws Exception {
        Resources owner = createResources();
        mockMvc.perform(post("/api/reports/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":" + owner.resumeId() + ",\"jobId\":" + owner.jobId() + "}")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.report.status").value("PARTIAL"))
                .andExpect(jsonPath("$.data.report.jobMatch.status").value("MISSING"))
                .andExpect(jsonPath("$.data.report.resumeAnalysis.status").value("MISSING"))
                .andExpect(jsonPath("$.data.report.interviewQuestions.status").value("MISSING"));
    }

    @Test
    void resumeOnlyAnalysisSucceedsWithoutCreatingFinalReport() throws Exception {
        Resources owner = createResources();
        when(llmClient.complete(any())).thenReturn(response(resumeAnalysisJson()));
        String response = mockMvc.perform(post("/api/resumes/{resumeId}/analyze", owner.resumeId())
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long taskId = objectMapper.readTree(response).path("data").path("taskId").asLong();
        awaitStatus(taskId, WorkflowStatus.SUCCESS);
        assertThat(resumeAnalysisReportRepository.findAll()).hasSize(1);
        assertThat(finalReportRepository.findAll()).isEmpty();
    }

    @Test
    void runsMvpSmokeFlowFromUploadToFinalReport() throws Exception {
        AuthenticatedUser owner = registerAndLogin();
        when(llmClient.complete(any()))
                .thenReturn(response(jobMatchJson()))
                .thenReturn(response(resumeAnalysisJson()))
                .thenReturn(response(interviewQuestionsJson()));

        Long resumeDocumentId = uploadParseChunkAndEmbed(
                owner.token(), "resume.txt", "RESUME",
                "Java Spring Boot PostgreSQL Redis backend engineer with reliable service experience"
        );
        String resumeResponse = mockMvc.perform(post("/api/resumes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentId\":" + resumeDocumentId + ",\"title\":\"Backend Resume\"}")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long resumeId = objectMapper.readTree(resumeResponse).path("data").path("resumeId").asLong();

        Long jobDocumentId = uploadParseChunkAndEmbed(
                owner.token(), "job.txt", "JD",
                "Senior backend engineer building Spring services with PostgreSQL and Redis"
        );
        String jobResponse = mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentId\":" + jobDocumentId
                                + ",\"company\":\"Example Inc\",\"position\":\"Senior Backend Engineer\"}")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long jobId = objectMapper.readTree(jobResponse).path("data").path("jobId").asLong();

        Long taskId = createTask(owner.token(), resumeId, jobId, null);
        awaitStatus(taskId, WorkflowStatus.SUCCESS);

        mockMvc.perform(get("/api/career-tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.progress").value(100));
        mockMvc.perform(get("/api/career-tasks/{taskId}/logs", taskId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(10));
        mockMvc.perform(get("/api/reports").header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("COMPLETE"));
    }

    private Long uploadParseChunkAndEmbed(String token, String fileName, String fileType, String content)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", content.getBytes());
        String uploadResponse = mockMvc.perform(multipart("/api/files/upload")
                        .file(file).param("fileType", fileType)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long fileId = objectMapper.readTree(uploadResponse).path("data").path("fileId").asLong();
        String parseResponse = mockMvc.perform(post("/api/files/{fileId}/parse", fileId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Long documentId = objectMapper.readTree(parseResponse).path("data").path("documentId").asLong();
        mockMvc.perform(post("/api/documents/{documentId}/chunks", documentId)
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mockMvc.perform(post("/api/documents/{documentId}/embeddings", documentId)
                        .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        return documentId;
    }

    private Long createTask(String token, Long resumeId, Long jobId, String enabledStep) throws Exception {
        String steps = enabledStep == null ? "" : ",\"enabledSteps\":[\"" + enabledStep + "\"]";
        String response = mockMvc.perform(post("/api/career-tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":" + resumeId + ",\"jobId\":" + jobId + steps + "}")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("taskId").asLong();
    }

    private Long createMatchTask(Resources owner) throws Exception {
        String response = mockMvc.perform(post("/api/jobs/{jobId}/match", owner.jobId())
                        .param("resumeId", owner.resumeId().toString())
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabledSteps[0]").value("MATCHING_JOB"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("taskId").asLong();
    }

    private Long createAnalysisTask(Resources owner) throws Exception {
        String response = mockMvc.perform(post("/api/resumes/{resumeId}/analyze", owner.resumeId())
                        .param("jobId", owner.jobId().toString())
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabledSteps[0]").value("ANALYZING_RESUME"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("taskId").asLong();
    }

    private Long createQuestionTask(Resources owner) throws Exception {
        String response = mockMvc.perform(post("/api/interview/questions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":" + owner.resumeId() + ",\"jobId\":" + owner.jobId() + "}")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabledSteps[0]").value("GENERATING_QUESTIONS"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("taskId").asLong();
    }

    private Resources createResources() throws Exception {
        AuthenticatedUser authenticated = registerAndLogin();
        User user = userRepository.findByEmail(authenticated.email()).orElseThrow();
        Document document = documentRepository.save(new Document(
                user.getId(), null, FileType.RESUME, "resume.txt", "Java Spring PostgreSQL experience", Map.of()
        ));
        Resume resume = resumeRepository.save(new Resume(user.getId(), document.getId(), "Backend Resume"));
        Job job = jobRepository.save(new Job(
                user.getId(), null, "Example Inc", "Senior Backend Engineer", "Build reliable Spring services"
        ));
        return new Resources(authenticated.token(), resume.getId(), job.getId());
    }

    private AuthenticatedUser registerAndLogin() throws Exception {
        String email = "workflow-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"nickname\":\"Workflow\"}"))
                .andExpect(status().isOk());
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(response);
        return new AuthenticatedUser(email, root.path("data").path("accessToken").asText());
    }

    private AgentTask awaitStatus(Long taskId, WorkflowStatus status) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(8));
        while (Instant.now().isBefore(deadline)) {
            AgentTask task = taskRepository.findById(taskId).orElseThrow();
            if (task.getStatus() == status) return task;
            if (task.getStatus() == WorkflowStatus.FAILED) throw new AssertionError("Task failed: " + task.getErrorMessage());
            Thread.sleep(20);
        }
        throw new AssertionError("Task did not reach " + status);
    }

    private void awaitNoRunningTasks() {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(8));
        while (Instant.now().isBefore(deadline)) {
            if (taskRepository.findAll().stream().noneMatch(task -> !task.getStatus().isTerminal())) return;
            try { Thread.sleep(20); } catch (InterruptedException exception) { Thread.currentThread().interrupt(); return; }
        }
    }

    private LlmResponse response(String content) {
        return new LlmResponse(content, "TEST", "mock", "stop", new LlmResponse.TokenUsage(10, 20, 30), 1, UUID.randomUUID().toString());
    }

    private String jobMatchJson() {
        return """
                {"matchScore":82,"summary":"Good fit","strengths":["Java"],"weaknesses":[],
                 "missingSkills":[],"suggestedResumeChanges":["Add metrics"],"citations":[]}
                """;
    }

    private String resumeAnalysisJson() {
        return """
                {"summary":"Strong backend profile","highlights":["Spring"],"weaknesses":[],"projectIssues":[],
                 "suggestions":["Add metrics"],"risks":[],"nextActions":["Practice design"],"citations":[]}
                """;
    }

    private String interviewQuestionsJson() {
        return """
                {"questions":[
                  {"question":"Explain transaction isolation","questionType":"TECHNICAL","difficulty":"MEDIUM",
                   "expectedPoints":["Isolation levels"],"citations":[],"noCitationReason":"General technical question"},
                  {"question":"Design a task platform","questionType":"SYSTEM_DESIGN","difficulty":"HARD",
                   "expectedPoints":["Reliability"],"citations":[],"noCitationReason":"General system design question"}
                ]}
                """;
    }

    private record AuthenticatedUser(String email, String token) { }
    private record Resources(String token, Long resumeId, Long jobId) { }
}
