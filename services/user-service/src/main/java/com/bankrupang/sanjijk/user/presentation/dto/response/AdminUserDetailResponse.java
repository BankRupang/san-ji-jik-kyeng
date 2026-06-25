package com.bankrupang.sanjijk.user.presentation.dto.response;

import com.bankrupang.sanjijk.user.domain.UserRole;
import com.bankrupang.sanjijk.user.domain.UserStatus;
import com.bankrupang.sanjijk.user.domain.entity.User;

import java.util.UUID;

/**
 * 관리자 전용 유저 단일 조회 응답 DTO.
 * 사업자번호(businessNumber), 슬랙ID(slackId) 등 민감 정보는 포함하지 않음.
 * 본인 정보 조회(/users/me)는 UserResponse를 사용.
 */
public record AdminUserDetailResponse(
        UUID userId,
        String username,
        String name,
        String email,
        String phone,
        UserRole role,
        UserStatus status
) {
    public static AdminUserDetailResponse from(User user) {
        return new AdminUserDetailResponse(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getStatus()
        );
    }
}
