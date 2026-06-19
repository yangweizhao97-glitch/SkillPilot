package com.huatai.careeragent.knowledge.chunk;

import com.huatai.careeragent.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class TextChunker {
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+(.+)$");
    private static final Set<String> DOMAIN_SECTIONS = Set.of(
            "教育经历", "项目经历", "实习经历", "工作经历", "技能栈", "专业技能", "奖项证书",
            "岗位职责", "职位职责", "任职要求", "岗位要求", "加分项", "技术栈", "业务描述"
    );

    private final ApproxTokenCounter tokenCounter;

    public TextChunker(ApproxTokenCounter tokenCounter) {
        this.tokenCounter = tokenCounter;
    }

    public List<TextChunk> chunk(ChunkSource source, ChunkingOptions options) {
        if (source == null || !StringUtils.hasText(source.contentText())) {
            throw new BusinessException("DOCUMENT_CONTENT_EMPTY", "Document content is empty", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        List<TextBlock> blocks = toBlocks(source.contentText());
        List<TextChunk> chunks = new ArrayList<>();
        List<TextBlock> window = new ArrayList<>();
        int windowTokens = 0;

        for (TextBlock block : blocks) {
            if (block.tokenCount() > options.targetTokens()) {
                flushWindow(source, options, chunks, window, windowTokens);
                window.clear();
                windowTokens = 0;
                appendLargeBlock(source, options, chunks, block);
                continue;
            }

            if (!window.isEmpty() && windowTokens + block.tokenCount() > options.targetTokens()) {
                flushWindow(source, options, chunks, window, windowTokens);
                List<TextBlock> overlap = overlapBlocks(window, options.overlapTokens());
                window.clear();
                window.addAll(overlap);
                windowTokens = overlap.stream().mapToInt(TextBlock::tokenCount).sum();
            }
            window.add(block);
            windowTokens += block.tokenCount();
        }

        flushWindow(source, options, chunks, window, windowTokens);
        return chunks;
    }

    private List<TextBlock> toBlocks(String contentText) {
        List<TextBlock> blocks = new ArrayList<>();
        String currentLocator = "Document";
        StringBuilder paragraph = new StringBuilder();
        int paragraphStartLine = 1;
        int lineNumber = 0;

        for (String rawLine : contentText.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            lineNumber++;
            String line = rawLine.strip();
            String heading = detectHeading(line);
            if (heading != null) {
                addParagraph(blocks, paragraph, currentLocator, paragraphStartLine, lineNumber - 1);
                paragraph.setLength(0);
                currentLocator = heading;
                blocks.add(new TextBlock(line, currentLocator, lineNumber, lineNumber, tokenCounter.count(line)));
                paragraphStartLine = lineNumber + 1;
                continue;
            }
            if (line.isBlank()) {
                addParagraph(blocks, paragraph, currentLocator, paragraphStartLine, lineNumber - 1);
                paragraph.setLength(0);
                paragraphStartLine = lineNumber + 1;
                continue;
            }
            if (paragraph.isEmpty()) {
                paragraphStartLine = lineNumber;
            } else {
                paragraph.append('\n');
            }
            paragraph.append(line);
        }

        addParagraph(blocks, paragraph, currentLocator, paragraphStartLine, lineNumber);
        return blocks;
    }

    private String detectHeading(String line) {
        if (!StringUtils.hasText(line)) {
            return null;
        }
        java.util.regex.Matcher matcher = MARKDOWN_HEADING.matcher(line);
        if (matcher.matches()) {
            return matcher.group(1).strip();
        }
        String normalized = line.replace("：", "").replace(":", "").strip();
        if (normalized.length() <= 20 && DOMAIN_SECTIONS.contains(normalized)) {
            return normalized;
        }
        return null;
    }

    private void addParagraph(List<TextBlock> blocks, StringBuilder paragraph, String locator, int startLine, int endLine) {
        String text = paragraph.toString().strip();
        if (text.isBlank()) {
            return;
        }
        blocks.add(new TextBlock(text, locator, startLine, endLine, tokenCounter.count(text)));
    }

    private void flushWindow(
            ChunkSource source,
            ChunkingOptions options,
            List<TextChunk> chunks,
            List<TextBlock> window,
            int windowTokens
    ) {
        if (window.isEmpty()) {
            return;
        }
        String content = joinBlocks(window);
        chunks.add(new TextChunk(
                chunks.size(),
                source.sourceType(),
                source.sourceTitle(),
                locatorFor(window),
                content,
                windowTokens
        ));
    }

    private List<TextBlock> overlapBlocks(List<TextBlock> window, int overlapTokens) {
        List<TextBlock> overlap = new ArrayList<>();
        int tokens = 0;
        for (int i = window.size() - 1; i >= 0; i--) {
            TextBlock block = window.get(i);
            if (!overlap.isEmpty() && tokens + block.tokenCount() > overlapTokens) {
                break;
            }
            overlap.add(0, block);
            tokens += block.tokenCount();
            if (tokens >= overlapTokens) {
                break;
            }
        }
        return overlap;
    }

    private void appendLargeBlock(ChunkSource source, ChunkingOptions options, List<TextChunk> chunks, TextBlock block) {
        List<String> tokens = tokenCounter.tokenize(block.text());
        int start = 0;
        while (start < tokens.size()) {
            int end = Math.min(start + options.targetTokens(), tokens.size());
            String content = String.join(" ", tokens.subList(start, end));
            chunks.add(new TextChunk(
                    chunks.size(),
                    source.sourceType(),
                    source.sourceTitle(),
                    block.locator() + " lines " + block.startLine() + "-" + block.endLine(),
                    content,
                    end - start
            ));
            if (end == tokens.size()) {
                break;
            }
            start = Math.max(end - options.overlapTokens(), start + 1);
        }
    }

    private String joinBlocks(List<TextBlock> blocks) {
        List<String> texts = blocks.stream().map(TextBlock::text).toList();
        return String.join("\n\n", texts);
    }

    private String locatorFor(List<TextBlock> blocks) {
        TextBlock first = blocks.getFirst();
        TextBlock last = blocks.getLast();
        return first.locator() + " lines " + first.startLine() + "-" + last.endLine();
    }

    private record TextBlock(String text, String locator, int startLine, int endLine, int tokenCount) {
    }
}
