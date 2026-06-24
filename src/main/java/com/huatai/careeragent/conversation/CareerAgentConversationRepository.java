package com.huatai.careeragent.conversation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CareerAgentConversationRepository extends JpaRepository<CareerAgentConversation, Long> {
    List<CareerAgentConversation> findByUserIdOrderByUpdatedAtDescIdDesc(Long userId);
    Optional<CareerAgentConversation> findByIdAndUserId(Long id, Long userId);
}
