package com.huatai.careeragent.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CareerAgentMessageRepository extends JpaRepository<CareerAgentMessage, Long> {
    List<CareerAgentMessage> findByUserIdAndConversationIdOrderByCreatedAtAscIdAsc(Long userId, Long conversationId);
    List<CareerAgentMessage> findTop12ByUserIdAndConversationIdOrderByCreatedAtDescIdDesc(Long userId, Long conversationId);
}
