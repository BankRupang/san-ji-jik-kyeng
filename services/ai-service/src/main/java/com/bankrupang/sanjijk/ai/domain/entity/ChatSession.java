package com.bankrupang.sanjijk.ai.domain.entity;

import com.bankrupang.sanjijk.ai.domain.enums.SessionStatus;
import com.bankrupang.sanjijk.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_chat_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession extends BaseEntity {

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(nullable = false)
    private LocalDateTime expiredAt;

    public static ChatSession create(UUID userId, int expireHours) {
        ChatSession session = new ChatSession();
        session.userId = userId;
        session.status = SessionStatus.ACTIVE;
        session.expiredAt = LocalDateTime.now().plusHours(expireHours);
        return session;
    }

    public boolean isExpired() {
        return status == SessionStatus.EXPIRED || LocalDateTime.now().isAfter(expiredAt);
    }

    public void expire() {
        this.status = SessionStatus.EXPIRED;
    }
}
