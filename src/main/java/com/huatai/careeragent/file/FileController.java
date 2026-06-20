package com.huatai.careeragent.file;

import com.huatai.careeragent.common.api.ApiResponse;
import com.huatai.careeragent.common.api.PageResponse;
import com.huatai.careeragent.common.security.CurrentUser;
import com.huatai.careeragent.file.FileDtos.ParseFileResponse;
import com.huatai.careeragent.file.FileDtos.UploadedFileResponse;
import com.huatai.careeragent.file.FileDtos.ProcessFileResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public ApiResponse<UploadedFileResponse> upload(
            CurrentUser currentUser,
            @RequestParam("fileType") FileType fileType,
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.ok(fileService.upload(currentUser.userId(), fileType, file));
    }

    @GetMapping
    public ApiResponse<PageResponse<UploadedFileResponse>> list(
            CurrentUser currentUser,
            @RequestParam(value = "fileType", required = false) FileType fileType,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        return ApiResponse.ok(fileService.list(currentUser.userId(), fileType, page, pageSize));
    }

    @GetMapping("/{fileId}")
    public ApiResponse<UploadedFileResponse> get(CurrentUser currentUser, @PathVariable Long fileId) {
        return ApiResponse.ok(fileService.get(currentUser.userId(), fileId));
    }

    @PostMapping("/{fileId}/parse")
    public ApiResponse<ParseFileResponse> parse(CurrentUser currentUser, @PathVariable Long fileId) {
        return ApiResponse.ok(fileService.parse(currentUser.userId(), fileId));
    }

    @PostMapping("/{fileId}/process")
    public ApiResponse<ProcessFileResponse> process(CurrentUser currentUser, @PathVariable Long fileId) {
        return ApiResponse.ok(fileService.process(currentUser.userId(), fileId));
    }
}
