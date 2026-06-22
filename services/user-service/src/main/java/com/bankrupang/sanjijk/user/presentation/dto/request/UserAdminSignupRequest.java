package com.bankrupang.sanjijk.user.presentation.dto.request;

import com.bankrupang.sanjijk.user.domain.UserRole;
import jakarta.validation.constraints.*;

import java.util.UUID;

public record UserAdminSignupRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z0-9]{4,12}$", message = "username은 4~12자 소문자와 숫자만 가능합니다")
        String username,

        @NotBlank
        @Size(max = 20, message = "이름은 20자 이하여야 합니다")
        String name,

        @NotBlank
        @Email(message = "이메일 형식이 올바르지 않습니다")
        String email,

        @Pattern(regexp = "^01[016789]-\\d{3,4}-\\d{4}$", message = "전화번호 형식이 올바르지 않습니다")
        String phone,

        @NotBlank
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=]).{8,15}$",
                message = "password는 8~15자, 대소문자, 숫자, 특수문자를 포함해야 합니다")
        String password,

        @NotBlank
        String slackId,

        boolean notificationAllow,

        @NotNull
        UserRole role,

        @NotBlank
        String adminKey
) {
    public UserAdminSignupRequest {
        username = cleanString(username);
        name = cleanString(name);
        email = cleanString(email);
        phone = cleanString(phone);
        adminKey = cleanString(adminKey);
    }

    private static String cleanString(String input) {
        if (input == null) return null;
        String trimmed = input.strip();
        return trimmed.isBlank() ? null : trimmed;
    }
}
