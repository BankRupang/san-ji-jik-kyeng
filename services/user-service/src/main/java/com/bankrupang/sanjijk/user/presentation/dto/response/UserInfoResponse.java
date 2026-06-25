package com.bankrupang.sanjijk.user.presentation.dto.response;

import com.bankrupang.sanjijk.user.domain.entity.User;

import java.util.UUID;

public record UserInfoResponse(
        String name,
        String slackId
) {
    public static UserInfoResponse from(User user) {
        return new UserInfoResponse(
                user.getName(),
                user.getSlackId()
        );
    }
}
