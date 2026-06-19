package com.huatai.careeragent.job;

import com.huatai.careeragent.common.api.PageResponse;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.document.Document;
import com.huatai.careeragent.document.DocumentRepository;
import com.huatai.careeragent.file.FileType;
import com.huatai.careeragent.job.JobDtos.CreateJobRequest;
import com.huatai.careeragent.job.JobDtos.JobResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class JobService {
    private final JobRepository jobRepository;
    private final DocumentRepository documentRepository;

    public JobService(JobRepository jobRepository, DocumentRepository documentRepository) {
        this.jobRepository = jobRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional
    public JobResponse create(Long userId, CreateJobRequest request) {
        Long documentId = request.documentId();
        String jdText = request.jdText();
        if (documentId != null) {
            Document document = documentRepository.findByIdAndUserId(documentId, userId)
                    .orElseThrow(() -> new BusinessException("DOCUMENT_NOT_FOUND", "Document not found", HttpStatus.NOT_FOUND));
            if (document.getDocType() != FileType.JD) {
                throw new BusinessException("DOCUMENT_TYPE_MISMATCH", "Document must be a JD", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            jdText = document.getContentText();
        }
        if (!StringUtils.hasText(jdText)) {
            throw new BusinessException("JD_TEXT_REQUIRED", "JD text is required", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Job job = new Job(
                userId,
                documentId,
                cleanOptional(request.company()),
                request.position().trim(),
                jdText.trim()
        );
        return JobResponse.from(jobRepository.save(job));
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> list(Long userId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<Job> result = jobRepository.findByUserId(
                userId,
                PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new PageResponse<>(
                result.map(JobResponse::from).getContent(),
                safePage,
                safePageSize,
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public JobResponse get(Long userId, Long jobId) {
        Job job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new BusinessException("JOB_NOT_FOUND", "Job not found", HttpStatus.NOT_FOUND));
        return JobResponse.from(job);
    }

    private String cleanOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
