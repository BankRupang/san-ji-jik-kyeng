package com.bankrupang.sanjijk.ai.presentation.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatResponse {

    private String message;

    public static ChatResponse of(String message) {
        return ChatResponse.builder()
                .message(message)
                .build();
    }
}
