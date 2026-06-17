package com.bankrupang.sanjijk.user.presentation.dto.request;

import com.bankrupang.sanjijk.user.domain.UserRole;
import jakarta.validation.constraints.*;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public record UserSignupRequest (
        @NotBlank
//        @Pattern(regexp = "asdf", message = "username은 4~12자 소문자와 숫자만 가능합니다")
        String username,

        @NotBlank
        @Size(max = 20, message = "이름은 20자 이하여야 합니다")
        String name,

        @NotBlank
        @Email(message = "이메일 형식이 올바르지 않습니다")
        String email,

//        @Pattern(regexp ="", message = "전화번호 형식이 올바르지 않습니다")
        String phone,

        @NotBlank
//        @Pattern(regexp = "", message = "password는 8~15wk, 대소문자, 숫자, 특수문자를 포함해야 합니다")
        String password,

        @NotBlank
//        @Pattern(regexp = "", message = "사업자등록번호 형식이 올바르지 않습니다. (예: 123-45-6789")
        String businessNumber,

        @NotNull
        UUID slackId,

        boolean notificationAllow,

        @NotNull
        UserRole role
) {
    public UserSignupRequest {
        // 모든 필드 공백 제거 및 빈 문자열은 null 처리
        username = cleanString(username);
        name = cleanString(name);
        email = cleanString(email);
        phone = cleanString(phone);
        businessNumber = cleanString(businessNumber);
    }

    // 정제 로직을 별도 private 메서드로 분리 (Record 내부 선언 가능)
    private static String cleanString(String input) {
        if (input == null) return null;
        String trimmed = input.strip(); // trim()보다 유니코드 공백까지 잘 잡아주는 strip() 권장
        return trimmed.isBlank() ? null : trimmed;
    }
}
