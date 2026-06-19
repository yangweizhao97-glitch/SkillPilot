package com.huatai.careeragent.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "career-agent.storage.upload-dir=target/test-uploads",
        "career-agent.storage.max-upload-size=20MB"
})
class FileControllerTest {
    private static final Path TEST_UPLOAD_DIR = Path.of("target/test-uploads");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UploadedFileRepository uploadedFileRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() throws Exception {
        uploadedFileRepository.deleteAll();
        userRepository.deleteAll();
        FileSystemUtils.deleteRecursively(TEST_UPLOAD_DIR);
    }

    @Test
    void uploadsFileAndReturnsMetadata() throws Exception {
        String token = registerAndLogin();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../resume.md",
                "text/markdown",
                "# Resume\nJava backend developer".getBytes()
        );

        String response = mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("fileType", "RESUME")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", startsWith("trace_")))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileName").value("resume.md"))
                .andExpect(jsonPath("$.data.fileType").value("RESUME"))
                .andExpect(jsonPath("$.data.mimeType").value("text/markdown"))
                .andExpect(jsonPath("$.data.parseStatus").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long fileId = objectMapper.readTree(response).path("data").path("fileId").asLong();
        UploadedFile uploadedFile = uploadedFileRepository.findById(fileId).orElseThrow();
        assert Files.exists(Path.of(uploadedFile.getStoragePath()));
    }

    @Test
    void listsAndGetsOnlyCurrentUsersFiles() throws Exception {
        String ownerToken = registerAndLogin();
        String otherToken = registerAndLogin();
        Long fileId = uploadTextFile(ownerToken, "jd.txt", "JD", "Java engineer JD");

        mockMvc.perform(get("/api/files")
                        .queryParam("page", "1")
                        .queryParam("pageSize", "20")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].fileId").value(fileId))
                .andExpect(jsonPath("$.data.items[0].fileType").value("JD"))
                .andExpect(jsonPath("$.data.totalItems").value(1));

        mockMvc.perform(get("/api/files/{fileId}", fileId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").value(fileId));

        mockMvc.perform(get("/api/files/{fileId}", fileId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"));
    }

    @Test
    void rejectsUnsupportedMimeType() throws Exception {
        String token = registerAndLogin();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "script.sh",
                "application/x-sh",
                "rm -rf /".getBytes()
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("fileType", "NOTE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void rejectsUploadWithoutAuthentication() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.txt",
                "text/plain",
                "hello".getBytes()
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("fileType", "RESUME"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    private Long uploadTextFile(String token, String fileName, String fileType, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                fileName,
                "text/plain",
                content.getBytes()
        );

        String response = mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("fileType", fileType)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("fileId").asLong();
    }

    private String registerAndLogin() throws Exception {
        String email = "file-user-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "password123",
                                  "nickname": "File User"
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
