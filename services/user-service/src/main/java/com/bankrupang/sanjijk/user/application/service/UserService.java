package com.bankrupang.sanjijk.user.application.service;

import com.bankrupang.sanjijk.common.response.PageResponse;
import com.bankrupang.sanjijk.common.util.PageableUtils;
import com.bankrupang.sanjijk.user.domain.UserRole;
import com.bankrupang.sanjijk.user.domain.UserStatus;
import com.bankrupang.sanjijk.user.domain.entity.User;
import com.bankrupang.sanjijk.user.domain.exception.*;
import com.bankrupang.sanjijk.user.domain.repository.UserRepository;
import com.bankrupang.sanjijk.user.domain.repository.UserSpecification;
import com.bankrupang.sanjijk.user.infrastructure.config.RedisKeys;
import com.bankrupang.sanjijk.user.infrastructure.keycloak.KeycloakService;
import com.bankrupang.sanjijk.user.presentation.dto.request.*;
import com.bankrupang.sanjijk.user.presentation.dto.response.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;
    private final StringRedisTemplate redisTemplate;

    @Value("${admin.manager-key}")
    private String managerKey;

    @Value("${admin.master-key}")
    private String masterKey;

    // 일반 사용자 가입
    @Transactional
    public UserResponse signup(UserSignupRequest request) {
        validateUserRole(request.role());
        validateDuplicateUser(request.username());
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

    // 관리자 가입
    @Transactional
    public UserResponse adminSignup(UserAdminSignupRequest request) {
        validateAdminRole(request.role());
        validateAdminKey(request.role(), request.adminKey());
        validateDuplicateUser(request.username());

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

    // 로그인
    @Transactional
    public UserLoginResponse login(UserLoginRequest request) {
        // 1. DB에서 유저 상태 확인 (탈퇴 여부)
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(UserNotFoundException::new);

        user.validateStatusForLogin();

        // 2. Keycloak에 로그인 요청 → 토큰 받기
        KeycloakTokenResponse token = keycloakService.login(request.username(), request.password());

        // 3. UserLoginResponse로 변환해서 반환
        return new UserLoginResponse(token.accessToken(), token.refreshToken());

    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId) {
        User user = findUserByIdOrElseThrow(userId);
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserListResponse> getUsers(UserRole role, UserStatus status, int page, int size) {
        Pageable pageable = PageableUtils.ofDefault(page, size);

        Specification<User> spec = UserSpecification.hasRole(role)
                .and(UserSpecification.hasStatus(status));

        Page<User> users = userRepository.findAll(spec, pageable);

        return PageResponse.of(users.map(UserListResponse::from));
    }

    // 유저 프로필 수정
    @Transactional
    public UserResponse updateUserInfo(UUID userId, UserInfoUpdateRequest request) {
        User user = findUserByIdOrElseThrow(userId);

        user.updateUserInfo(request.name(), request.phone(), request.slackId());
        return UserResponse.from(user);
    }

    // 사업자 번호 수정
    @Transactional
    public UserResponse updateBusinessNumber(UUID userId, UserBusinessNumberUpdateRequest request) {
        User user = findUserByIdOrElseThrow(userId);

        user.updateBusinessNumber(request.businessNumber());
        return UserResponse.from(user);
    }

    // 유저 탈퇴
    @Transactional
    public void deleteUser(UUID userId) {
        User user = findUserByIdOrElseThrow(userId);
        user.deleteUser();
    }

    // 유저 계정 일시정지 [마스터 전용]
    @Transactional
    public void suspendUser(UserSuspendedRequest request) {
        User user = findUserByIdOrElseThrow(request.userId());
        user.suspendUser();
        redisTemplate.opsForSet().add(RedisKeys.SUSPENDED_USERS, request.userId().toString());
    }

    // 유저 계정 일시정지 혜지 [마스터 전용]
    @Transactional
    public void unsuspendUser(UserSuspendedRequest request) {
        User user = findUserByIdOrElseThrow(request.userId());
        user.unsuspendUser();
        redisTemplate.opsForSet().remove(RedisKeys.SUSPENDED_USERS, request.userId().toString());
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

    // 중복 검증 (username)
    private void validateDuplicateUser(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new UserUsernameExistsException();
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

    // 알림 허용 여부 검증 [internal]
    @Transactional(readOnly = true)
    public UserNotifyResponse getNotificationAllow(UUID userId) {
        return UserNotifyResponse.from(findUserByIdOrElseThrow(userId));
    }

    // 사용자 정보 조회 [internal]
    @Transactional(readOnly = true)
    public UserInfoResponse getUserInfo(UUID userId) {
        return UserInfoResponse.from(findUserByIdOrElseThrow(userId));
    }

    private User findUserByIdOrElseThrow(UUID userId) {
        return userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
    }
}
