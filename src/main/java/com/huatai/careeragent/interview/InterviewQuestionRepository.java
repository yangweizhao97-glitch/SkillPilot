package com.huatai.careeragent.interview;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long>,
        JpaSpecificationExecutor<InterviewQuestion> {
}
