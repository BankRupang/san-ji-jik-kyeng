package com.bankrupang.sanjijk.ai.domain.entity;

import com.bankrupang.sanjijk.ai.domain.enums.ChatRole;
import com.bankrupang.sanjijk.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "p_chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage extends BaseEntity {

    @Column(nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public static ChatMessage of(UUID sessionId, ChatRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.sessionId = sessionId;
        message.role = role;
        message.content = content;
        return message;
    }
}
