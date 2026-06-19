package com.huatai.careeragent.resume;

import com.huatai.careeragent.common.api.PageResponse;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.document.Document;
import com.huatai.careeragent.document.DocumentRepository;
import com.huatai.careeragent.file.FileType;
import com.huatai.careeragent.resume.ResumeDtos.CreateResumeRequest;
import com.huatai.careeragent.resume.ResumeDtos.ResumeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeService {
    private final ResumeRepository resumeRepository;
    private final DocumentRepository documentRepository;

    public ResumeService(ResumeRepository resumeRepository, DocumentRepository documentRepository) {
        this.resumeRepository = resumeRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional
    public ResumeResponse create(Long userId, CreateResumeRequest request) {
        Document document = documentRepository.findByIdAndUserId(request.documentId(), userId)
                .orElseThrow(() -> new BusinessException("DOCUMENT_NOT_FOUND", "Document not found", HttpStatus.NOT_FOUND));
        if (document.getDocType() != FileType.RESUME) {
            throw new BusinessException("DOCUMENT_TYPE_MISMATCH", "Document must be a resume", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Resume resume = new Resume(userId, document.getId(), request.title().trim());
        return ResumeResponse.from(resumeRepository.save(resume));
    }

    @Transactional(readOnly = true)
    public PageResponse<ResumeResponse> list(Long userId, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<Resume> result = resumeRepository.findByUserId(
                userId,
                PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new PageResponse<>(
                result.map(ResumeResponse::from).getContent(),
                safePage,
                safePageSize,
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public ResumeResponse get(Long userId, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> new BusinessException("RESUME_NOT_FOUND", "Resume not found", HttpStatus.NOT_FOUND));
        return ResumeResponse.from(resume);
    }
}
