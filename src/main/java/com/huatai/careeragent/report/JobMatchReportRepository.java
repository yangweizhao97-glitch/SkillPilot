package com.huatai.careeragent.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobMatchReportRepository extends JpaRepository<JobMatchReport, Long> {
    List<JobMatchReport> findByUserIdAndJobIdOrderByVersionDesc(Long userId, Long jobId);
    Optional<JobMatchReport> findFirstByUserIdAndResumeIdAndJobIdOrderByVersionDesc(
            Long userId, Long resumeId, Long jobId
    );
    Optional<JobMatchReport> findByUserIdAndTaskId(Long userId, Long taskId);

    @Query("select coalesce(max(r.version), 0) from JobMatchReport r where r.resumeId = :resumeId and r.jobId = :jobId")
    int maxVersion(@Param("resumeId") Long resumeId, @Param("jobId") Long jobId);
}
