package com.bankrupang.sanjijk.user.presentation.dto.request;

public record UserInfoUpdateRequest(
        String name,
        String phone,
        String slackId
) {
}
