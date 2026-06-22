package com.huatai.careeragent.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huatai.careeragent.document.Document;
import com.huatai.careeragent.document.DocumentRepository;
import com.huatai.careeragent.knowledge.chunk.DocumentChunkRepository;
import com.huatai.careeragent.user.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.FileSystemUtils;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        "career-agent.storage.max-upload-size=20MB",
        "career-agent.chunking.target-tokens=8",
        "career-agent.chunking.overlap-tokens=2"
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
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() throws Exception {
        documentChunkRepository.deleteAll();
        documentRepository.deleteAll();
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

    @Test
    void parsesFileAndCreatesDocument() throws Exception {
        String token = registerAndLogin();
        Long fileId = uploadTextFile(token, "resume.md", "RESUME", "# Resume\nJava backend developer");

        String response = mockMvc.perform(post("/api/files/{fileId}/parse", fileId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileId").value(fileId))
                .andExpect(jsonPath("$.data.parseStatus").value("SUCCESS"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long documentId = objectMapper.readTree(response).path("data").path("documentId").asLong();
        UploadedFile uploadedFile = uploadedFileRepository.findById(fileId).orElseThrow();
        Document document = documentRepository.findById(documentId).orElseThrow();
        assertThat(uploadedFile.getParseStatus()).isEqualTo(ParseStatus.SUCCESS);
        assertThat(uploadedFile.getParsedText()).contains("Java backend developer");
        assertThat(document.getFileId()).isEqualTo(fileId);
        assertThat(document.getDocType()).isEqualTo(FileType.RESUME);
        assertThat(document.getContentText()).contains("Java backend developer");
    }

    @Test
    void processesPdfResumeFile() throws Exception {
        String token = registerAndLogin();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "resume.pdf",
                "application/pdf",
                pdfBytes("Java backend developer with Spring Boot PostgreSQL")
        );

        String uploadResponse = mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("fileType", "RESUME")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long fileId = objectMapper.readTree(uploadResponse).path("data").path("fileId").asLong();

        String processResponse = mockMvc.perform(post("/api/files/{fileId}/process", fileId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long documentId = objectMapper.readTree(processResponse).path("data").path("documentId").asLong();
        UploadedFile uploadedFile = uploadedFileRepository.findById(fileId).orElseThrow();
        Document document = documentRepository.findById(documentId).orElseThrow();
        Integer embeddedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE document_id = ? AND embedding IS NOT NULL",
                Integer.class,
                documentId
        );
        assertThat(uploadedFile.getParseStatus()).isEqualTo(ParseStatus.READY);
        assertThat(document.getContentText()).contains("Java backend developer");
        assertThat(embeddedCount).isGreaterThan(0);
    }

    @Test
    void marksFileFailedWhenParsedContentIsEmpty() throws Exception {
        String token = registerAndLogin();
        Long fileId = uploadTextFile(token, "blank.txt", "NOTE", "   \n\t  ");

        mockMvc.perform(post("/api/files/{fileId}/parse", fileId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("DOCUMENT_CONTENT_EMPTY"));

        UploadedFile uploadedFile = uploadedFileRepository.findById(fileId).orElseThrow();
        assertThat(uploadedFile.getParseStatus()).isEqualTo(ParseStatus.FAILED);
        assertThat(uploadedFile.getErrorMessage()).contains("Document content is empty");
        assertThat(documentRepository.findByFileIdAndUserId(fileId, uploadedFile.getUserId())).isEmpty();
    }

    @Test
    void repeatParseOverwritesExistingDocument() throws Exception {
        String token = registerAndLogin();
        Long fileId = uploadTextFile(token, "jd.txt", "JD", "Java engineer JD");

        Long firstDocumentId = parseFile(token, fileId);
        Long secondDocumentId = parseFile(token, fileId);

        assertThat(secondDocumentId).isEqualTo(firstDocumentId);
        assertThat(documentRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsParseForOtherUsersFile() throws Exception {
        String ownerToken = registerAndLogin();
        String otherToken = registerAndLogin();
        Long fileId = uploadTextFile(ownerToken, "private.txt", "NOTE", "private notes");

        mockMvc.perform(post("/api/files/{fileId}/parse", fileId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FILE_NOT_FOUND"));
    }

    @Test
    void chunksParsedDocumentAndListsChunks() throws Exception {
        String token = registerAndLogin();
        Long fileId = uploadTextFile(token, "resume.md", "RESUME", """
                # Resume
                Java backend developer with Spring Boot PostgreSQL Redis

                项目经历
                Built CareerAgent upload parse chunk pipeline
                """);
        Long documentId = parseFile(token, fileId);

        mockMvc.perform(post("/api/documents/{documentId}/chunks", documentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(documentId))
                .andExpect(jsonPath("$.data.chunkCount").value(4))
                .andExpect(jsonPath("$.data.chunks[0].chunkIndex").value(0))
                .andExpect(jsonPath("$.data.chunks[0].sourceType").value("RESUME"))
                .andExpect(jsonPath("$.data.chunks[0].sourceTitle").value("resume.md"))
                .andExpect(jsonPath("$.data.chunks[0].sourceLocator").exists())
                .andExpect(jsonPath("$.data.chunks[0].tokenCount").exists());

        mockMvc.perform(get("/api/documents/{documentId}/chunks", documentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chunkCount").value(4))
                .andExpect(jsonPath("$.data.chunks[1].sourceLocator").value(startsWith("Resume")));

        mockMvc.perform(post("/api/documents/{documentId}/chunks", documentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chunkCount").value(4));

        assertThat(documentChunkRepository.findByDocumentIdAndUserIdOrderByChunkIndexAsc(documentId, currentUserId(documentId)))
                .hasSize(4);
    }

    @Test
    void rejectsChunkingOtherUsersDocument() throws Exception {
        String ownerToken = registerAndLogin();
        String otherToken = registerAndLogin();
        Long fileId = uploadTextFile(ownerToken, "jd.txt", "JD", "Java engineer JD");
        Long documentId = parseFile(ownerToken, fileId);

        mockMvc.perform(post("/api/documents/{documentId}/chunks", documentId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DOCUMENT_NOT_FOUND"));
    }

    @Test
    void embedsChunksAndSearchesCurrentUsersKnowledge() throws Exception {
        String ownerToken = registerAndLogin();
        Long resumeFileId = uploadTextFile(ownerToken, "resume.md", "RESUME", """
                # Resume
                Spring Boot JWT PostgreSQL Redis

                项目经历
                CareerAgent document parsing chunk embedding retrieval
                """);
        Long resumeDocumentId = parseFile(ownerToken, resumeFileId);
        chunkDocument(ownerToken, resumeDocumentId);

        String otherToken = registerAndLogin();
        Long otherFileId = uploadTextFile(otherToken, "other.md", "NOTE", "unrelated private notes about finance");
        Long otherDocumentId = parseFile(otherToken, otherFileId);
        chunkDocument(otherToken, otherDocumentId);

        mockMvc.perform(post("/api/documents/{documentId}/embeddings", resumeDocumentId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(resumeDocumentId))
                .andExpect(jsonPath("$.data.embeddedChunkCount").value(3))
                .andExpect(jsonPath("$.data.model").value("local-hashing-1024"));

        Integer embeddedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE document_id = ? AND embedding IS NOT NULL",
                Integer.class,
                resumeDocumentId
        );
        assertThat(embeddedCount).isEqualTo(3);

        mockMvc.perform(post("/api/knowledge/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "Spring Boot PostgreSQL",
                                  "sourceTypes": ["RESUME"],
                                  "topK": 3,
                                  "retrievalMode": "HYBRID"
                                }
                                """)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].citationId").value(startsWith("chunk_")))
                .andExpect(jsonPath("$.data.items[0].documentId").value(resumeDocumentId))
                .andExpect(jsonPath("$.data.items[0].sourceType").value("RESUME"))
                .andExpect(jsonPath("$.data.items[0].sourceTitle").value("resume.md"))
                .andExpect(jsonPath("$.data.items[0].content").value(org.hamcrest.Matchers.containsString("Spring Boot")))
                .andExpect(jsonPath("$.data.items[0].score").exists());

        mockMvc.perform(post("/api/knowledge/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "Spring Boot PostgreSQL",
                                  "sourceTypes": ["RESUME"],
                                  "topK": 3,
                                  "retrievalMode": "KEYWORD"
                                }
                                """)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void rejectsEmbeddingOtherUsersDocument() throws Exception {
        String ownerToken = registerAndLogin();
        String otherToken = registerAndLogin();
        Long fileId = uploadTextFile(ownerToken, "resume.txt", "RESUME", "Spring Boot resume");
        Long documentId = parseFile(ownerToken, fileId);
        chunkDocument(ownerToken, documentId);

        mockMvc.perform(post("/api/documents/{documentId}/embeddings", documentId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DOCUMENT_NOT_FOUND"));
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

    private Long parseFile(String token, Long fileId) throws Exception {
        String response = mockMvc.perform(post("/api/files/{fileId}/parse", fileId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("SUCCESS"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("documentId").asLong();
    }

    private byte[] pdfBytes(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private void chunkDocument(String token, Long documentId) throws Exception {
        mockMvc.perform(post("/api/documents/{documentId}/chunks", documentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private Long currentUserId(Long documentId) {
        return documentRepository.findById(documentId).orElseThrow().getUserId();
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
