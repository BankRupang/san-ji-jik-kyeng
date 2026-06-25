package com.bankrupang.sanjijk.ai.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ChatRequest {

    @NotBlank(message = "메시지를 입력해주세요.")
    @Size(max = 2000, message = "메시지는 2000자 이하로 입력해주세요.")
    private String message;
}
