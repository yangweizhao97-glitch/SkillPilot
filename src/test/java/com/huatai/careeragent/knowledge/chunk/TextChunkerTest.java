package com.huatai.careeragent.knowledge.chunk;

import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.file.FileType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextChunkerTest {
    private final TextChunker textChunker = new TextChunker(new ApproxTokenCounter());

    @Test
    void chunksShortTextIntoSingleChunk() {
        List<TextChunk> chunks = textChunker.chunk(
                new ChunkSource(FileType.NOTE, "note.txt", "Spring Boot JWT auth"),
                new ChunkingOptions(10, 2)
        );

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().chunkIndex()).isZero();
        assertThat(chunks.getFirst().sourceType()).isEqualTo(FileType.NOTE);
        assertThat(chunks.getFirst().sourceTitle()).isEqualTo("note.txt");
        assertThat(chunks.getFirst().sourceLocator()).contains("Document");
        assertThat(chunks.getFirst().tokenCount()).isEqualTo(4);
    }

    @Test
    void chunksLongTextWithOverlapPredictably() {
        String content = IntStream.rangeClosed(1, 25)
                .mapToObj(i -> "word" + i)
                .reduce((left, right) -> left + " " + right)
                .orElseThrow();

        List<TextChunk> chunks = textChunker.chunk(
                new ChunkSource(FileType.NOTE, "long.txt", content),
                new ChunkingOptions(10, 3)
        );

        assertThat(chunks).hasSize(4);
        assertThat(chunks).extracting(TextChunk::chunkIndex).containsExactly(0, 1, 2, 3);
        assertThat(chunks.get(0).content()).startsWith("word1").contains("word10");
        assertThat(chunks.get(1).content()).startsWith("word8");
        assertThat(chunks.get(2).content()).startsWith("word15");
        assertThat(chunks.get(3).content()).startsWith("word22");
    }

    @Test
    void rejectsEmptyText() {
        assertThatThrownBy(() -> textChunker.chunk(
                new ChunkSource(FileType.NOTE, "empty.txt", "  \n "),
                new ChunkingOptions(10, 2)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Document content is empty");
    }

    @Test
    void usesHeadingsAndDomainSectionsAsLocators() {
        String content = """
                # Resume
                Java developer

                项目经历
                CareerAgent backend with RAG

                技能栈
                Spring Boot PostgreSQL Redis
                """;

        List<TextChunk> chunks = textChunker.chunk(
                new ChunkSource(FileType.RESUME, "resume.md", content),
                new ChunkingOptions(6, 1)
        );

        assertThat(chunks).extracting(TextChunk::sourceLocator)
                .anyMatch(locator -> locator.contains("Resume"))
                .anyMatch(locator -> locator.contains("项目经历"))
                .anyMatch(locator -> locator.contains("技能栈"));
    }

    @Test
    void producesStableOrderForRepeatedChunking() {
        String content = "岗位职责\n负责后端开发\n\n任职要求\n熟悉 Java Spring Boot PostgreSQL Redis";

        List<TextChunk> first = textChunker.chunk(
                new ChunkSource(FileType.JD, "jd.txt", content),
                new ChunkingOptions(5, 1)
        );
        List<TextChunk> second = textChunker.chunk(
                new ChunkSource(FileType.JD, "jd.txt", content),
                new ChunkingOptions(5, 1)
        );

        assertThat(second).containsExactlyElementsOf(first);
    }
}
