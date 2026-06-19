package com.huatai.careeragent.job;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public final class JobDtos {
    private JobDtos() {
    }

    public record CreateJobRequest(
            Long documentId,
            String company,
            @NotBlank String position,
            String jdText
    ) {
    }

    public record JobResponse(
            Long jobId,
            Long documentId,
            String company,
            String position,
            String jdText,
            Instant createdAt
    ) {
        public static JobResponse from(Job job) {
            return new JobResponse(
                    job.getId(),
                    job.getDocumentId(),
                    job.getCompany(),
                    job.getPosition(),
                    job.getJdText(),
                    job.getCreatedAt()
            );
        }
    }
}
