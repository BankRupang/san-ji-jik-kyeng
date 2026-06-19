package com.bankrupang.sanjijk.user.presentation.dto.response;

public record UserLoginResponse(
        String accessToken,
        String refreshToken
) {
}
