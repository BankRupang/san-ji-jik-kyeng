package com.bankrupang.sanjijk.user.presentation.dto.response;

import com.bankrupang.sanjijk.user.domain.entity.User;

import java.util.UUID;

public record UserNotifyResponse(
        UUID userId,
        String slackId,
        boolean notificationAllow

) {
    public static UserNotifyResponse from(User user) {
        return new UserNotifyResponse(
                user.getId(),
                user.getSlackId(),
                user.isNotificationAllow()
        );
    }
}
