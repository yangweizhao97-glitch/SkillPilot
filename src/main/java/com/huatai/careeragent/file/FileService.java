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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.huatai.careeragent.knowledge.chunk.DocumentChunkService;
import com.huatai.careeragent.knowledge.embedding.DocumentEmbeddingService;
import com.huatai.careeragent.file.FileDtos.ProcessFileResponse;

@Service
public class FileService {
    private final UploadedFileRepository uploadedFileRepository;
    private final LocalFileStorageService localFileStorageService;
    private final DocumentParserService documentParserService;
    private final DocumentRepository documentRepository;
    private final DocumentChunkService chunkService;
    private final DocumentEmbeddingService embeddingService;

    public FileService(
            UploadedFileRepository uploadedFileRepository,
            LocalFileStorageService localFileStorageService,
            DocumentParserService documentParserService,
            DocumentRepository documentRepository,
            DocumentChunkService chunkService,
            DocumentEmbeddingService embeddingService
    ) {
        this.uploadedFileRepository = uploadedFileRepository;
        this.localFileStorageService = localFileStorageService;
        this.documentParserService = documentParserService;
        this.documentRepository = documentRepository;
        this.chunkService = chunkService;
        this.embeddingService = embeddingService;
    }

    @Transactional
    public UploadedFileResponse upload(Long userId, FileType fileType, MultipartFile file) {
        StoredFile storedFile = localFileStorageService.store(userId, file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) localFileStorageService.deleteQuietly(storedFile.storagePath());
            }
        });
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

    @Transactional(noRollbackFor = BusinessException.class)
    public ProcessFileResponse process(Long userId, Long fileId) {
        UploadedFile file = uploadedFileRepository.findByIdAndUserId(fileId, userId)
                .orElseThrow(() -> new BusinessException("FILE_NOT_FOUND", "File not found", HttpStatus.NOT_FOUND));
        try {
            file.markStatus(ParseStatus.PARSING);
            ParseFileResponse parsed = parse(userId, fileId);
            file.markStatus(ParseStatus.CHUNKING);
            int chunks = chunkService.chunkDocument(userId, parsed.documentId()).chunkCount();
            file.markStatus(ParseStatus.EMBEDDING);
            int embedded = embeddingService.embedDocument(userId, parsed.documentId()).embeddedChunkCount();
            file.markStatus(ParseStatus.READY);
            return new ProcessFileResponse(fileId, parsed.documentId(), ParseStatus.READY, chunks, embedded);
        } catch (BusinessException exception) {
            file.markParseFailed(exception.getMessage());
            throw exception;
        }
    }
}
