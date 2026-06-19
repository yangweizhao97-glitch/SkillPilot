package com.huatai.careeragent.file;

import com.huatai.careeragent.common.api.PageResponse;
import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.document.Document;
import com.huatai.careeragent.document.DocumentRepository;
import com.huatai.careeragent.file.FileDtos.ParseFileResponse;
import com.huatai.careeragent.file.FileDtos.UploadedFileResponse;
import com.huatai.careeragent.file.parser.DocumentParseResult;
import com.huatai.careeragent.file.parser.DocumentParserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
    private final UploadedFileRepository uploadedFileRepository;
    private final LocalFileStorageService localFileStorageService;
    private final DocumentParserService documentParserService;
    private final DocumentRepository documentRepository;

    public FileService(
            UploadedFileRepository uploadedFileRepository,
            LocalFileStorageService localFileStorageService,
            DocumentParserService documentParserService,
            DocumentRepository documentRepository
    ) {
        this.uploadedFileRepository = uploadedFileRepository;
        this.localFileStorageService = localFileStorageService;
        this.documentParserService = documentParserService;
        this.documentRepository = documentRepository;
    }

    @Transactional
    public UploadedFileResponse upload(Long userId, FileType fileType, MultipartFile file) {
        StoredFile storedFile = localFileStorageService.store(userId, file);
        UploadedFile uploadedFile = new UploadedFile(
                userId,
                storedFile.originalFileName(),
                fileType,
                storedFile.mimeType(),
                storedFile.storagePath(),
                storedFile.sizeBytes()
        );
        return UploadedFileResponse.from(uploadedFileRepository.save(uploadedFile));
    }

    @Transactional(readOnly = true)
    public PageResponse<UploadedFileResponse> list(Long userId, FileType fileType, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        PageRequest pageRequest = PageRequest.of(
                safePage - 1,
                safePageSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<UploadedFile> result = fileType == null
                ? uploadedFileRepository.findByUserId(userId, pageRequest)
                : uploadedFileRepository.findByUserIdAndFileType(userId, fileType, pageRequest);

        return new PageResponse<>(
                result.map(UploadedFileResponse::from).getContent(),
                safePage,
                safePageSize,
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public UploadedFileResponse get(Long userId, Long fileId) {
        UploadedFile uploadedFile = uploadedFileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException("FILE_NOT_FOUND", "File not found", HttpStatus.NOT_FOUND));
        return UploadedFileResponse.from(uploadedFile);
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public ParseFileResponse parse(Long userId, Long fileId) {
        UploadedFile uploadedFile = uploadedFileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException("FILE_NOT_FOUND", "File not found", HttpStatus.NOT_FOUND));

        DocumentParseResult parseResult;
        try {
            parseResult = documentParserService.parse(uploadedFile);
        } catch (BusinessException e) {
            uploadedFile.markParseFailed(e.getMessage());
            throw e;
        }

        uploadedFile.markParseSuccess(parseResult.contentText());
        Document document = documentRepository.findByFileIdAndUserId(uploadedFile.getId(), userId)
                .orElseGet(() -> new Document(
                        userId,
                        uploadedFile.getId(),
                        uploadedFile.getFileType(),
                        uploadedFile.getFileName(),
                        parseResult.contentText(),
                        parseResult.metadata()
                ));
        document.replaceContent(uploadedFile.getFileName(), parseResult.contentText(), parseResult.metadata());
        Document savedDocument = documentRepository.save(document);
        return ParseFileResponse.success(uploadedFile, savedDocument.getId());
    }
}
