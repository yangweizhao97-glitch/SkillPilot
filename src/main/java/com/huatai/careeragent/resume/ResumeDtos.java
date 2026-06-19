package com.huatai.careeragent.resume;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class ResumeDtos {
    private ResumeDtos() {
    }

    public record CreateResumeRequest(@NotNull Long documentId, @NotBlank String title) {
    }

    public record ResumeResponse(
            Long resumeId,
            Long documentId,
            String title,
            int latestAnalysisVersion,
            Instant createdAt
    ) {
        public static ResumeResponse from(Resume resume) {
            return new ResumeResponse(
                    resume.getId(),
                    resume.getDocumentId(),
                    resume.getTitle(),
                    resume.getLatestAnalysisVersion(),
                    resume.getCreatedAt()
            );
        }
    }
}
