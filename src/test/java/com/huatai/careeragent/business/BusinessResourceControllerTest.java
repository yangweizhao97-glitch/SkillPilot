package com.huatai.careeragent.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.document.DocumentRepository;
import com.huatai.careeragent.file.UploadedFileRepository;
import com.huatai.careeragent.job.JobRepository;
import com.huatai.careeragent.resume.ResumeRepository;
import com.huatai.careeragent.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Path;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "career-agent.storage.upload-dir=target/test-business-uploads",
        "career-agent.storage.max-upload-size=20MB"
})
class BusinessResourceControllerTest {
    private static final Path TEST_UPLOAD_DIR = Path.of("target/test-business-uploads");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() throws Exception {
        jobRepository.deleteAll();
        resumeRepository.deleteAll();
        documentRepository.deleteAll();
        uploadedFileRepository.deleteAll();
        userRepository.deleteAll();
        FileSystemUtils.deleteRecursively(TEST_UPLOAD_DIR);
    }

    @Test
    void createsListsAndGetsResumeFromResumeDocument() throws Exception {
        String ownerToken = registerAndLogin();
        String otherToken = registerAndLogin();
        Long documentId = uploadAndParse(ownerToken, "resume.md", "RESUME", "# Resume\nJava backend developer");

        String response = mockMvc.perform(post("/api/resumes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": %d,
                                  "title": "Java Backend Resume"
                                }
                                """.formatted(documentId))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(documentId))
                .andExpect(jsonPath("$.data.title").value("Java Backend Resume"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long resumeId = objectMapper.readTree(response).path("data").path("resumeId").asLong();

        mockMvc.perform(get("/api/resumes")
                        .queryParam("page", "1")
                        .queryParam("pageSize", "20")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].resumeId").value(resumeId));

        mockMvc.perform(get("/api/resumes/{resumeId}", resumeId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resumeId").value(resumeId));

        mockMvc.perform(get("/api/resumes/{resumeId}", resumeId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESUME_NOT_FOUND"));
    }

    @Test
    void rejectsResumeCreatedFromWrongDocumentTypeOrOtherUsersDocument() throws Exception {
        String ownerToken = registerAndLogin();
        String otherToken = registerAndLogin();
        Long jdDocumentId = uploadAndParse(ownerToken, "jd.txt", "JD", "Java engineer JD");

        mockMvc.perform(post("/api/resumes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": %d,
                                  "title": "Wrong Type"
                                }
                                """.formatted(jdDocumentId))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("DOCUMENT_TYPE_MISMATCH"));

        mockMvc.perform(post("/api/resumes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": %d,
                                  "title": "Other User"
                                }
                                """.formatted(jdDocumentId))
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void createsListsAndGetsJobFromDocumentAndText() throws Exception {
        String token = registerAndLogin();
        Long jdDocumentId = uploadAndParse(token, "jd.txt", "JD", "岗位职责\nSpring Boot PostgreSQL Redis");

        String fromDocumentResponse = mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": %d,
                                  "company": "Example Inc",
                                  "position": "Java Backend Intern"
                                }
                                """.formatted(jdDocumentId))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(jdDocumentId))
                .andExpect(jsonPath("$.data.company").value("Example Inc"))
                .andExpect(jsonPath("$.data.position").value("Java Backend Intern"))
                .andExpect(jsonPath("$.data.jdText").value(containsString("Spring Boot")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long documentJobId = objectMapper.readTree(fromDocumentResponse).path("data").path("jobId").asLong();

        String fromTextResponse = mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "company": "Text Co",
                                  "position": "AI Engineer",
                                  "jdText": "负责 RAG 和 Agent 系统开发"
                                }
                                """)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").doesNotExist())
                .andExpect(jsonPath("$.data.company").value("Text Co"))
                .andExpect(jsonPath("$.data.jdText").value("负责 RAG 和 Agent 系统开发"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long textJobId = objectMapper.readTree(fromTextResponse).path("data").path("jobId").asLong();

        mockMvc.perform(get("/api/jobs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2));

        mockMvc.perform(get("/api/jobs/{jobId}", documentJobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(documentJobId));

        mockMvc.perform(get("/api/jobs/{jobId}", textJobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(textJobId));
    }

    @Test
    void rejectsInvalidJobInputsAndOtherUsersAccess() throws Exception {
        String ownerToken = registerAndLogin();
        String otherToken = registerAndLogin();
        Long resumeDocumentId = uploadAndParse(ownerToken, "resume.txt", "RESUME", "Java backend resume");

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "documentId": %d,
                                  "position": "Java Backend"
                                }
                                """.formatted(resumeDocumentId))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("DOCUMENT_TYPE_MISMATCH"));

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "position": "Java Backend"
                                }
                                """)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("JD_TEXT_REQUIRED"));

        String jobResponse = mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "position": "Java Backend",
                                  "jdText": "Spring Boot JD"
                                }
                                """)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long jobId = objectMapper.readTree(jobResponse).path("data").path("jobId").asLong();

        mockMvc.perform(get("/api/jobs/{jobId}", jobId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("JOB_NOT_FOUND"));
    }

    private Long uploadAndParse(String token, String fileName, String fileType, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                "text/plain",
                content.getBytes()
        );

        String uploadResponse = mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("fileType", fileType)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long fileId = objectMapper.readTree(uploadResponse).path("data").path("fileId").asLong();

        String parseResponse = mockMvc.perform(post("/api/files/{fileId}/parse", fileId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(parseResponse).path("data").path("documentId").asLong();
    }

    private String registerAndLogin() throws Exception {
        String email = "business-user-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "nickname": "Business User"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk());

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(loginResponse);
        return root.path("data").path("accessToken").asText();
    }
}
