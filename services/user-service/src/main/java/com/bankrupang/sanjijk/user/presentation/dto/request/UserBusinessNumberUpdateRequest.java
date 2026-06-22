package com.bankrupang.sanjijk.user.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserBusinessNumberUpdateRequest(
        @NotBlank
        @Pattern(regexp = "^\\d{3}-\\d{2}-\\d{5}$", message = "사업자등록번호 형식이 올바르지 않습니다. (예: 123-45-67890)")
        String businessNumber
) {
}
