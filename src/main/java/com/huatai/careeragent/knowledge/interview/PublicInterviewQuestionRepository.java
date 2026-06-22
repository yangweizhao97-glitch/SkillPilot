package com.huatai.careeragent.knowledge.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PublicInterviewQuestionRepository extends JpaRepository<PublicInterviewQuestion, Long> {
    List<PublicInterviewQuestion> findByExperienceIdOrderByIdAsc(Long experienceId);
}
