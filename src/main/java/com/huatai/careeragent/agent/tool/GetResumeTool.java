package com.huatai.careeragent.agent.tool;

import com.huatai.careeragent.document.Document;
import com.huatai.careeragent.document.DocumentRepository;
import com.huatai.careeragent.resume.Resume;
import com.huatai.careeragent.resume.ResumeRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
public class GetResumeTool implements Tool<GetResumeTool.Input, GetResumeTool.Output> {
    public static final String NAME = "getResume";

    private final ResumeRepository resumeRepository;
    private final DocumentRepository documentRepository;

    public GetResumeTool(ResumeRepository resumeRepository, DocumentRepository documentRepository) {
        this.resumeRepository = resumeRepository;
        this.documentRepository = documentRepository;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public Set<String> allowedAgents() {
        return Set.of(AgentNames.JOB_MATCH_AGENT, AgentNames.RESUME_ANALYSIS_AGENT, AgentNames.INTERVIEW_QUESTION_AGENT);
    }

    @Override
    @Transactional(readOnly = true)
    public Output execute(Input input, ToolExecutionContext context) {
        Resume resume = resumeRepository.findByIdAndUserId(input.resumeId(), context.userId())
                .orElseThrow(() -> new ToolException("RESUME_NOT_FOUND", "Resume not found", false));
        Document document = documentRepository.findByIdAndUserId(resume.getDocumentId(), context.userId())
                .orElseThrow(() -> new ToolException("RESUME_DOCUMENT_NOT_FOUND", "Resume document not found", false));
        return new Output(resume.getId(), resume.getTitle(), document.getContentText());
    }

    public record Input(@NotNull Long resumeId) {
    }

    public record Output(Long resumeId, String title, String content) {
    }
}
