package com.huatai.careeragent.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FinalReportRepository extends JpaRepository<FinalReport, Long> {
    List<FinalReport> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
    Optional<FinalReport> findByIdAndUserId(Long id, Long userId);

    @Query("select coalesce(max(r.version), 0) from FinalReport r "
            + "where r.resumeId = :resumeId and r.jobId = :jobId")
    int maxVersion(@Param("resumeId") Long resumeId, @Param("jobId") Long jobId);
}

