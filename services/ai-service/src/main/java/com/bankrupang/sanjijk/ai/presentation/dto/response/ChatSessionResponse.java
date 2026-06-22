package com.bankrupang.sanjijk.ai.presentation.dto.response;

import com.bankrupang.sanjijk.ai.domain.entity.ChatSession;
import com.bankrupang.sanjijk.ai.domain.enums.SessionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ChatSessionResponse {

    private UUID id;
    private SessionStatus status;
    private LocalDateTime expiredAt;
    private LocalDateTime createdAt;

    public static ChatSessionResponse from(ChatSession session) {
        return ChatSessionResponse.builder()
                .id(session.getId())
                .status(session.getStatus())
                .expiredAt(session.getExpiredAt())
                .createdAt(session.getCreatedAt())
                .build();
    }
}
