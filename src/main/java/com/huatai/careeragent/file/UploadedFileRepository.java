package com.huatai.careeragent.file;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {
    Page<UploadedFile> findByUserId(Long userId, Pageable pageable);

    Page<UploadedFile> findByUserIdAndFileType(Long userId, FileType fileType, Pageable pageable);

    Optional<UploadedFile> findByIdAndUserId(Long id, Long userId);
}
