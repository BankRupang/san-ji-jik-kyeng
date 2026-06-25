package com.bankrupang.sanjijk.user.presentation.dto.response;

import com.bankrupang.sanjijk.user.domain.UserRole;
import com.bankrupang.sanjijk.user.domain.UserStatus;
import com.bankrupang.sanjijk.user.domain.entity.User;

import java.util.UUID;

public record UserResponse (
        UUID userId,
        String username,
        String name,
        String email,
        String phone,
        String businessNumber,
        String slackId,
        boolean notificationAllow,
        UserRole role,
        UserStatus status
) {
    public static UserResponse from (User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getBusinessNumber(),
                user.getSlackId(),
                user.isNotificationAllow(),
                user.getRole(),
                user.getStatus()
        );
    }
}
