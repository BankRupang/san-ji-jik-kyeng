package com.bankrupang.sanjijk.ai.domain.repository;

import com.bankrupang.sanjijk.ai.domain.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    Page<ChatSession> findByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

    Optional<ChatSession> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByIdAndUserId(UUID id, UUID userId);
}
