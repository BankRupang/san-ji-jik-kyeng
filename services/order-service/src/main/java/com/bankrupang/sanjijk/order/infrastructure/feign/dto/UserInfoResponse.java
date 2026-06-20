package com.bankrupang.sanjijk.order.infrastructure.feign.dto;

import java.util.UUID;

public record UserInfoResponse(
        UUID userId,
        String userName,
        String slackId
) {
}
