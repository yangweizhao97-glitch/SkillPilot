package com.huatai.careeragent.knowledge.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InterviewExperienceRepository extends JpaRepository<InterviewExperience, Long> {
    List<InterviewExperience> findBySourceIdOrderByIdAsc(Long sourceId);
}
