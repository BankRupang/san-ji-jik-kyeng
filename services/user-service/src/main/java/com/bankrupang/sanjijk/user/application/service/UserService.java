package com.bankrupang.sanjijk.user.application.service;

import com.bankrupang.sanjijk.user.domain.UserRole;
import com.bankrupang.sanjijk.user.domain.exception.*;
import com.bankrupang.sanjijk.user.infrastructure.keycloak.KeycloakService;
import com.bankrupang.sanjijk.user.domain.entity.User;
import com.bankrupang.sanjijk.user.domain.repository.UserRepository;
import com.bankrupang.sanjijk.user.presentation.dto.request.UserAdminSignupRequest;
import com.bankrupang.sanjijk.user.presentation.dto.request.UserSignupRequest;
import com.bankrupang.sanjijk.user.presentation.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;

    @Value("${admin.manager-key}")
    private String managerKey;

    @Value("${admin.master-key}")
    private String masterKey;

    // 일반 회원가입 (BUYER / SELLER)
    @Transactional
    public UserResponse signup(UserSignupRequest request) {
        validateUserRole(request.role());
        validateDuplicateUser(request.username(), request.email());
        validateDuplicateBusinessNumber(request.businessNumber());

        UUID keycloakId = keycloakService.createUser(
                request.username(),
                request.email(),
                request.password(),
                request.role()
        );

        try {
            User savedUser = userRepository.save(User.create(
                    keycloakId,
                    request.username(),
                    request.name(),
                    request.email(),
                    request.phone(),
                    request.businessNumber(),
                    request.slackId(),
                    request.notificationAllow(),
                    request.role()
            ));
            return UserResponse.from(savedUser);
        } catch (Exception e) {
            rollbackKeycloak(keycloakId);
            throw new UserKeycloakCreationFailedException();
        }
    }

    // 관리자 회원가입 (MANAGER / MASTER)
    @Transactional
    public UserResponse adminSignup(UserAdminSignupRequest request) {
        validateAdminRole(request.role());
        validateAdminKey(request.role(), request.adminKey());
        validateDuplicateUser(request.username(), request.email());

        UUID keycloakId = keycloakService.createUser(
                request.username(),
                request.email(),
                request.password(),
                request.role()
        );

        try {
            User savedUser = userRepository.save(User.create(
                    keycloakId,
                    request.username(),
                    request.name(),
                    request.email(),
                    request.phone(),
                    null,  // 관리자는 사업자번호 없음
                    request.slackId(),
                    request.notificationAllow(),
                    request.role()
            ));
            return UserResponse.from(savedUser);
        } catch (Exception e) {
            rollbackKeycloak(keycloakId);
            throw new UserKeycloakCreationFailedException();
        }
    }

    // 일반 가입 롤 검증 — BUYER, SELLER만 허용
    private void validateUserRole(UserRole role) {
        if (role == UserRole.MANAGER || role == UserRole.MASTER) {
            throw new UserInvalidRoleForSignupException();
        }
    }

    // 관리자 가입 롤 검증 — MANAGER, MASTER만 허용
    private void validateAdminRole(UserRole role) {
        if (role == UserRole.BUYER || role == UserRole.SELLER) {
            throw new UserInvalidRoleForSignupException();
        }
    }

    // 관리자 키 검증
    private void validateAdminKey(UserRole role, String inputKey) {
        String expectedKey = (role == UserRole.MANAGER) ? managerKey : masterKey;
        if (!expectedKey.equals(inputKey)) {
            throw new UserInvalidAdminKeyException();
        }
    }

    // 중복 검증 (username, email)
    private void validateDuplicateUser(String username, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new UserUsernameExistsException();
        }
        if (userRepository.existsByEmail(email)) {
            throw new UserEmailExistsException();
        }
    }

    // 사업자번호 중복 검증
    private void validateDuplicateBusinessNumber(String businessNumber) {
        if (userRepository.existsByBusinessNumber(businessNumber)) {
            throw new UserBusinessNumberExistsException();
        }
    }

    // Keycloak 롤백 — 실패해도 로그만 남기고 원래 흐름 유지
    private void rollbackKeycloak(UUID keycloakId) {
        try {
            keycloakService.deleteUser(keycloakId);
        } catch (Exception e) {
            log.error("Keycloak 롤백 실패. 수동 삭제 필요. keycloakId={}", keycloakId, e);
        }
    }
}
