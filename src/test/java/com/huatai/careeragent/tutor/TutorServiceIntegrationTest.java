package com.huatai.careeragent.tutor;

import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.agent.tool.SearchUserKnowledgeBaseTool;
import com.huatai.careeragent.agent.tool.ToolCallLogRepository;
import com.huatai.careeragent.file.FileType;
import com.huatai.careeragent.knowledge.retrieval.KnowledgeDtos.KnowledgeSearchItem;
import com.huatai.careeragent.knowledge.retrieval.KnowledgeDtos.KnowledgeSearchRequest;
import com.huatai.careeragent.knowledge.retrieval.KnowledgeDtos.KnowledgeSearchResponse;
import com.huatai.careeragent.knowledge.retrieval.KnowledgeSearchService;
import com.huatai.careeragent.llm.LlmClient;
import com.huatai.careeragent.llm.LlmRequest;
import com.huatai.careeragent.llm.LlmResponse;
import com.huatai.careeragent.user.User;
import com.huatai.careeragent.user.UserRepository;
import com.huatai.careeragent.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class TutorServiceIntegrationTest {
    @Autowired private TutorService service;
    @Autowired private TutorMessageRepository messageRepository;
    @Autowired private TutorSessionRepository sessionRepository;
    @Autowired private ToolCallLogRepository toolCallLogRepository;
    @Autowired private UserRepository userRepository;
    @MockitoBean private LlmClient llmClient;
    @MockitoBean private KnowledgeSearchService knowledgeSearchService;

    @AfterEach
    void cleanUp() {
        toolCallLogRepository.deleteAll();
        messageRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void greetsANewUserWithoutInventingHistoryOrSearchingKnowledge() {
        User user = userRepository.save(new User(
                "tutor-greeting-" + UUID.randomUUID() + "@example.com", "hash", "Candidate", UserRole.USER
        ));
        var created = service.create(user.getId(), new TutorService.CreateTutorSessionRequest(
                null, null, null, null, null, null
        ));
        when(knowledgeSearchService.search(any(), any())).thenReturn(new KnowledgeSearchResponse(List.of()));
        when(llmClient.stream(any(), any())).thenAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("你好！我是你的 AI 学习导师，有什么可以帮你？");
            return new LlmResponse("你好！我是你的 AI 学习导师，有什么可以帮你？", "TEST", "mock",
                    "stop", LlmResponse.TokenUsage.empty(), 1, "request-greeting");
        });
        StringBuilder deltas = new StringBuilder();

        var answered = service.messageStreaming(user.getId(), created.sessionId(), "你好", deltas::append);

        assertThat(deltas).contains("你好", "AI 学习导师").doesNotContain("之前", "Java");
        assertThat(answered.messages()).hasSize(2);
        assertThat(answered.messages().getLast().content()).isEqualTo(deltas.toString());
        ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).stream(request.capture(), any());
        assertThat(request.getValue().messages().get(1).content())
                .contains("\"conversationState\":\"FIRST_TURN\"")
                .contains("\"retrievedSources\":[]")
                .doesNotContain("Java 相关");
    }

    @Test
    void executesPlannerSelectedToolWithTutorSessionScope() {
        User user = userRepository.save(new User(
                "tutor-tool-" + UUID.randomUUID() + "@example.com", "hash", "Candidate", UserRole.USER
        ));
        var created = service.create(user.getId(), new TutorService.CreateTutorSessionRequest(
                "工具答疑", null, null, null, null, null
        ));
        when(llmClient.complete(any())).thenReturn(new LlmResponse(
                """
                {"toolCalls":[{"toolName":"searchUserKnowledgeBase","arguments":{"query":"事务传播","topK":3}}]}
                """,
                "TEST", "mock", "stop", LlmResponse.TokenUsage.empty(), 1, "request-plan"));
        when(knowledgeSearchService.search(any(), any())).thenReturn(new KnowledgeSearchResponse(List.of(
                new KnowledgeSearchItem("chunk_tool_1", 3L, FileType.RESUME, "后端简历", "page:1",
                        "项目中使用 REQUIRES_NEW 隔离审计日志写入。", 0.93)
        )));
        when(llmClient.stream(any(), any())).thenAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("可以结合审计日志独立事务说明 [chunk_tool_1]");
            return new LlmResponse("可以结合审计日志独立事务说明 [chunk_tool_1]",
                    "TEST", "mock", "stop", LlmResponse.TokenUsage.empty(), 1, "request-answer");
        });

        var answered = service.messageStreaming(user.getId(), created.sessionId(), "结合我的项目解释事务传播",
                ignored -> { });

        assertThat(answered.messages().getLast().citations()).singleElement()
                .satisfies(citation -> assertThat(citation)
                        .containsEntry("citationId", "chunk_tool_1")
                        .containsEntry("sourceType", "PRIVATE_RESUME"));
        assertThat(toolCallLogRepository.findAll()).singleElement()
                .satisfies(log -> {
                    assertThat(log.getToolName()).isEqualTo(SearchUserKnowledgeBaseTool.NAME);
                    assertThat(log.getTaskId()).isNull();
                    assertThat(log.getAgentName()).isEqualTo("TUTOR_AGENT");
                });
    }

    @Test
    @SuppressWarnings("unchecked")
    void streamsPersistsCitationsKeepsContextAndEnforcesOwnership() {
        User user = userRepository.save(new User(
                "tutor-" + UUID.randomUUID() + "@example.com", "hash", "Candidate", UserRole.USER
        ));
        User intruder = userRepository.save(new User(
                "tutor-intruder-" + UUID.randomUUID() + "@example.com", "hash", "Intruder", UserRole.USER
        ));
        when(knowledgeSearchService.search(any(), any())).thenReturn(new KnowledgeSearchResponse(List.of(
                new KnowledgeSearchItem("chunk_9", 3L, FileType.RESUME, "后端简历", "page:1",
                        "项目使用 Spring 事务传播行为隔离审计写入。", 0.91)
        )));
        when(llmClient.stream(any(), any())).thenAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("事务传播行为决定方法如何加入事务");
            consumer.accept(" [chunk_9]");
            return new LlmResponse("事务传播行为决定方法如何加入事务 [chunk_9]", "TEST", "mock",
                    "stop", LlmResponse.TokenUsage.empty(), 1, "request-tutor");
        });

        var created = service.create(user.getId(), new TutorService.CreateTutorSessionRequest(
                "事务答疑", null, null, null, null, null
        ));
        StringBuilder deltas = new StringBuilder();
        var answered = service.messageStreaming(user.getId(), created.sessionId(), "什么是事务传播行为？",
                deltas::append);

        assertThat(deltas).contains("事务传播行为", "chunk_9");
        assertThat(answered.messages()).hasSize(2);
        assertThat(answered.messages().getLast().citations()).singleElement()
                .satisfies(citation -> assertThat(citation).containsEntry("citationId", "chunk_9")
                        .containsEntry("sourceType", "PRIVATE_RESUME"));
        assertThat(service.list(user.getId())).singleElement()
                .extracting(TutorService.TutorSessionSummary::title).isEqualTo("事务答疑");
        assertThatThrownBy(() -> service.get(intruder.getId(), created.sessionId()))
                .isInstanceOf(BusinessException.class);

        service.messageStreaming(user.getId(), created.sessionId(), "结合我的项目再解释一次。", ignored -> { });
        ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient, org.mockito.Mockito.times(2)).stream(request.capture(), any());
        assertThat(request.getAllValues().getLast().messages().get(1).content())
                .contains("什么是事务传播行为", "事务传播行为决定方法如何加入事务");
        ArgumentCaptor<KnowledgeSearchRequest> search = ArgumentCaptor.forClass(KnowledgeSearchRequest.class);
        verify(knowledgeSearchService, org.mockito.Mockito.times(2)).search(any(), search.capture());
        assertThat(search.getAllValues().getLast().query())
                .isEqualTo("结合我的项目再解释一次。");

        service.delete(user.getId(), created.sessionId());
        assertThat(service.list(user.getId())).isEmpty();
    }

    @Test
    void compactsOlderMessagesIntoPersistentMemoryAndKeepsChatting() {
        User user = userRepository.save(new User(
                "tutor-memory-" + UUID.randomUUID() + "@example.com", "hash", "Candidate", UserRole.USER
        ));
        when(knowledgeSearchService.search(any(), any())).thenReturn(new KnowledgeSearchResponse(List.of()));
        when(llmClient.stream(any(), any())).thenAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(1);
            consumer.accept("已回答");
            return new LlmResponse("已回答", "TEST", "mock", "stop",
                    LlmResponse.TokenUsage.empty(), 1, "request-memory");
        });
        var created = service.create(user.getId(), new TutorService.CreateTutorSessionRequest(
                "长期答疑", null, null, null, null, null
        ));

        for (int index = 1; index <= 8; index++) {
            service.messageStreaming(user.getId(), created.sessionId(),
                    index == 1 ? "我的核心目标是准备 Java 后端面试" : "继续第 " + index + " 个问题", ignored -> { });
        }

        var restored = service.get(user.getId(), created.sessionId());
        assertThat(restored.messages()).hasSize(16);
        assertThat(restored.memoryRevision()).isPositive();
        assertThat(restored.memoryThroughSequence()).isPositive();
        TutorSession stored = sessionRepository.findById(created.sessionId()).orElseThrow();
        assertThat(stored.getMemorySummary()).contains("准备 Java 后端面试", "已回答");

        ArgumentCaptor<LlmRequest> requests = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient, org.mockito.Mockito.times(8)).stream(requests.capture(), any());
        assertThat(requests.getAllValues().getLast().messages().get(1).content())
                .contains("sessionMemory", "准备 Java 后端面试", "recentConversation");
    }

    @Test
    void releasesProcessingClaimWhenGenerationFails() {
        User user = userRepository.save(new User(
                "tutor-failure-" + UUID.randomUUID() + "@example.com", "hash", "Candidate", UserRole.USER
        ));
        when(knowledgeSearchService.search(any(), any())).thenReturn(new KnowledgeSearchResponse(List.of()));
        when(llmClient.stream(any(), any())).thenThrow(new IllegalStateException("model unavailable"));
        var created = service.create(user.getId(), new TutorService.CreateTutorSessionRequest(
                null, null, null, null, null, null
        ));

        assertThatThrownBy(() -> service.messageStreaming(
                user.getId(), created.sessionId(), "解释一下 CAP", ignored -> { }
        )).isInstanceOf(IllegalStateException.class);
        var recovered = service.get(user.getId(), created.sessionId());
        assertThat(recovered.processing()).isFalse();
        assertThat(recovered.messages()).singleElement()
                .extracting(TutorService.TutorMessageResponse::role).isEqualTo(TutorMessageRole.USER);
    }
}
