package com.huatai.careeragent.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.document.Document;
import com.huatai.careeragent.document.DocumentRepository;
import com.huatai.careeragent.file.FileType;
import com.huatai.careeragent.job.Job;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmResponse;
import com.huatai.careeragent.resume.Resume;
import com.huatai.careeragent.resume.ResumeRepository;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InteractiveInterviewControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InterviewMessageRepository messageRepository;
    @Autowired private InterviewSessionRepository sessionRepository;
    @Autowired private InterviewQuestionRepository questionRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private LlmClient llmClient;

    @AfterEach
    void cleanUp() {
        messageRepository.deleteAll();
        sessionRepository.deleteAll();
        questionRepository.deleteAll();
        jobRepository.deleteAll();
        resumeRepository.deleteAll();
        documentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void streamsInterviewLifecycleAndDeltaEvents() throws Exception {
        Authenticated authenticated = registerAndLogin();
        User user = userRepository.findByEmail(authenticated.email()).orElseThrow();
        Document document = documentRepository.save(new Document(
                user.getId(), null, FileType.RESUME, "Resume", "Java Spring", Map.of()));
        Resume resume = resumeRepository.save(new Resume(user.getId(), document.getId(), "Backend"));
        Job job = jobRepository.save(new Job(user.getId(), null, "Acme", "Backend Engineer", "Build APIs"));
        questionRepository.save(new InterviewQuestion(user.getId(), resume.getId(), job.getId(), null,
                "请说明事务传播行为。", QuestionType.TECHNICAL, QuestionDifficulty.MEDIUM,
                List.of("REQUIRED", "REQUIRES_NEW"), List.of(), "General question"));
        when(llmClient.complete(any())).thenReturn(new LlmResponse(
                """
                {"schemaVersion":"1.0","overallScore":75,"dimensions":[
                  {"key":"accuracy","label":"准确性","score":75,"rationale":"基本准确"},
                  {"key":"relevance","label":"相关性","score":80,"rationale":"紧扣题意"},
                  {"key":"depth","label":"深度","score":65,"rationale":"缺少例子"},
                  {"key":"communication","label":"表达","score":85,"rationale":"清晰简洁"}
                ],"strengths":["概念正确"],"improvements":["增加真实场景"],
                "improvedAnswer":"可以结合独立事务场景解释。","followUp":true,
                "followUpQuestion":"请举例说明 REQUIRES_NEW。"}
                """,
                "TEST", "mock", "stop", LlmResponse.TokenUsage.empty(), 1, "request-1"));

        String createBody = mockMvc.perform(post("/api/interview/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":" + resume.getId() + ",\"jobId\":" + job.getId() + "}")
                        .header("Authorization", "Bearer " + authenticated.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long sessionId = objectMapper.readTree(createBody).path("data").path("sessionId").asLong();

        var stream = mockMvc.perform(post("/api/interview/sessions/{sessionId}/answers/stream", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":\"事务可以保证一致性。\"}")
                        .header("Authorization", "Bearer " + authenticated.token()))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:INTERVIEW_ANSWER_RECEIVED")))
                .andExpect(content().string(containsString("event:INTERVIEW_EVALUATING")))
                .andExpect(content().string(containsString("event:INTERVIEW_SCORING")))
                .andExpect(content().string(containsString("event:INTERVIEW_SCORE_COMPLETED")))
                .andExpect(content().string(containsString("event:INTERVIEW_FOLLOWUP_STREAMING")))
                .andExpect(content().string(containsString("event:INTERVIEW_FEEDBACK_COMPLETED")));
    }

    private Authenticated registerAndLogin() throws Exception {
        String email = "stream-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"nickname\":\"Stream\"}"))
                .andExpect(status().isOk());
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Authenticated(email, objectMapper.readTree(body).path("data").path("accessToken").asText());
    }

    private record Authenticated(String email, String token) { }
}
