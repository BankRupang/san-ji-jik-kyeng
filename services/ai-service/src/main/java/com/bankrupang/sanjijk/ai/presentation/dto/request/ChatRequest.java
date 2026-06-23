package com.bankrupang.sanjijk.ai.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class ChatRequest {

    @NotBlank(message = "메시지를 입력해주세요.")
    private String message;
}
