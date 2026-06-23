package com.bankrupang.sanjijk.user.presentation.dto.request;

import java.util.UUID;

public record UserSuspendedRequest(
        UUID userId
) {
}
