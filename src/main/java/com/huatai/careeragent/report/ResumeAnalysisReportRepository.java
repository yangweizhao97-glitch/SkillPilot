package com.huatai.careeragent.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResumeAnalysisReportRepository extends JpaRepository<ResumeAnalysisReport, Long> {
    List<ResumeAnalysisReport> findByUserIdAndResumeIdOrderByVersionDesc(Long userId, Long resumeId);
    Optional<ResumeAnalysisReport> findFirstByUserIdAndResumeIdOrderByVersionDesc(Long userId, Long resumeId);
    Optional<ResumeAnalysisReport> findByUserIdAndTaskId(Long userId, Long taskId);

    @Query("select coalesce(max(r.version), 0) from ResumeAnalysisReport r where r.resumeId = :resumeId")
    int maxVersion(@Param("resumeId") Long resumeId);
}
