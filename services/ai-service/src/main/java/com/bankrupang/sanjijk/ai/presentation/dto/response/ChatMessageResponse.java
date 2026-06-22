package com.bankrupang.sanjijk.ai.presentation.dto.response;

import com.bankrupang.sanjijk.ai.domain.entity.ChatMessage;
import com.bankrupang.sanjijk.ai.domain.enums.ChatRole;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ChatMessageResponse {

    private UUID id;
    private ChatRole role;
    private String content;
    private LocalDateTime createdAt;

    public static ChatMessageResponse from(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
