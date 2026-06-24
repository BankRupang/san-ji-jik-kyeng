package com.bankrupang.sanjijk.user.application;

import com.bankrupang.sanjijk.user.application.service.UserService;
import com.bankrupang.sanjijk.user.domain.UserRole;
import com.bankrupang.sanjijk.user.domain.UserStatus;
import com.bankrupang.sanjijk.user.domain.entity.User;
import com.bankrupang.sanjijk.user.domain.exception.*;
import com.bankrupang.sanjijk.user.domain.repository.UserRepository;
import com.bankrupang.sanjijk.user.infrastructure.keycloak.KeycloakService;
import com.bankrupang.sanjijk.user.presentation.dto.request.*;
import com.bankrupang.sanjijk.user.presentation.dto.response.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private KeycloakService keycloakService;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private SetOperations<String, String> setOperations;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "managerKey", "manager-secret");
        ReflectionTestUtils.setField(userService, "masterKey", "master-secret");
    }

    // 유저 가입
    private UserSignupRequest signupRequest() {
        return new UserSignupRequest(
                "testbuy", "구매자테스터", "testbuy@email.com",
                "010-1234-1234", "Password1234!", "123-12-12344",
                "slack-id-buyer", true, UserRole.BUYER
        );
    }

    // 관리자 가입
    private UserAdminSignupRequest adminSignupRequest() {
        return new UserAdminSignupRequest(
                "testad", "관리자테스터", "testad@email.com",
                "010-8888-9999","Password1234!", "slack-admin-id",
                true, UserRole.MANAGER, "manager-secret"
        );
    }

    // 로그인 테스트용
    private User activeUser() {
        return User.create(
                UUID.randomUUID(), "testuser", "테스트유저",
                "test@test.com", "010-1234-5678",
                "123-45-67890", "slack-id", true, UserRole.BUYER
        );
    }

    // ──────────────────────────────────────────────────
    // 일반 회원가입
    // ──────────────────────────────────────────────────
    @Nested
    @DisplayName("일반 회원가입")
    class Signup {

        @Test
        @DisplayName("성공")
        void success() {
            UUID keycloakId = UUID.randomUUID();
            User savedUser = User.create(
                    keycloakId,
                    signupRequest().username(),
                    signupRequest().name(),
                    signupRequest().email(),
                    signupRequest().phone(),
                    signupRequest().businessNumber(),
                    signupRequest().slackId(),
                    signupRequest().notificationAllow(),
                    signupRequest().role());

            given(userRepository.existsByUsername("testbuy")).willReturn(false);
            given(userRepository.existsByBusinessNumber("123-12-12344")).willReturn(false);
            given(keycloakService.createUser(anyString(), anyString(), anyString(), any())).willReturn(keycloakId);
            given(userRepository.save(any())).willReturn(savedUser);

            UserResponse result = userService.signup(signupRequest());

            assertThat(result.username()).isEqualTo("testbuy");
            assertThat(result.role()).isEqualTo(UserRole.BUYER);
            assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("username 중복 → UserUsernameExistsException")
        void duplicateUsername_throws() {
            given(userRepository.existsByUsername("testbuy")).willReturn(true);

            assertThatThrownBy(() -> userService.signup(signupRequest()))
                    .isInstanceOf(UserUsernameExistsException.class);
        }

        @Test
        @DisplayName("사업자번호 중복 → UserBusinessNumberExistsException")
        void duplicateBusinessNumber_throws() {
            given(userRepository.existsByUsername("testbuy")).willReturn(false);
            given(userRepository.existsByBusinessNumber("123-12-12344")).willReturn(true);

            assertThatThrownBy(() -> userService.signup(signupRequest()))
                    .isInstanceOf(UserBusinessNumberExistsException.class);
        }

        @Test
        @DisplayName("MANAGER 롤로 일반 가입 → UserInvalidRoleForSignupException")
        void managerRole_throws() {
            var req = new UserSignupRequest(
                    "testuser", "테스트유저", "test@test.com",
                    "010-1234-5678", "Password1!", "123-45-67890",
                    "slack-id", true, UserRole.MANAGER
            );
            assertThatThrownBy(() -> userService.signup(req))
                    .isInstanceOf(UserInvalidRoleForSignupException.class);
        }

        @Test
        @DisplayName("MASTER 롤로 일반 가입 → UserInvalidRoleForSignupException")
        void masterRole_throws() {
            var req = new UserSignupRequest(
                    "testuser", "테스트유저", "test@test.com",
                    "010-1234-5678", "Password1!", "123-45-67890",
                    "slack-id", true, UserRole.MASTER
            );
            assertThatThrownBy(() -> userService.signup(req))
                    .isInstanceOf(UserInvalidRoleForSignupException.class);
        }

        @Test
        @DisplayName("DB 저장실패 시 keycloak 롤백이 호출된다")
        void signup_dbSaveFail_keycloakRollback() {
            UUID keycloakId = UUID.randomUUID();

            given(userRepository.existsByUsername("testbuy")).willReturn(false);
            given(userRepository.existsByBusinessNumber("123-12-12344")).willReturn(false);
            given(keycloakService.createUser(anyString(), anyString(), anyString(), any()))
            .willReturn(keycloakId);
            given(userRepository.save(any())).willThrow(new RuntimeException("DB 오류"));

            assertThatThrownBy(() -> userService.signup(signupRequest()))
                    .isInstanceOf(UserKeycloakCreationFailedException.class);

            then(keycloakService).should().deleteUser(keycloakId);
        }
    }

    // ──────────────────────────────────────────────────
    // 관리자 회원가입
    // ──────────────────────────────────────────────────
    @Nested
    @DisplayName("관리자 회원가입")
    class AdminSignup {

        @Test
        @DisplayName("MANAGER 가입 성공")
        void managerSignup_success() {
            UUID keycloakId = UUID.randomUUID();
            User savedUser = User.create(
                    keycloakId,
                    adminSignupRequest().username(),
                    adminSignupRequest().name(),
                    adminSignupRequest().email(),
                    adminSignupRequest().phone(),
                    null,
                    adminSignupRequest().slackId(),
                    true,
                    adminSignupRequest().role());

            given(userRepository.existsByUsername("testad")).willReturn(false);
            given(keycloakService.createUser(anyString(), anyString(), anyString(), any())).willReturn(keycloakId);
            given(userRepository.save(any())).willReturn(savedUser);

            UserResponse result = userService.adminSignup(adminSignupRequest());

            assertThat(result.role()).isEqualTo(UserRole.MANAGER);
        }

        @Test
        @DisplayName("BUYER 롤로 관리자 가입 → UserInvalidRoleForSignupException")
        void buyerRole_throws() {
            var request = new UserAdminSignupRequest(
                    "testad", "관리자", "admin@test.com",
                    "010-9999-8888", "Password1!", "slack-admin",
                    true, UserRole.BUYER, "manager-secret"
            );
            assertThatThrownBy(() -> userService.adminSignup(request))
                    .isInstanceOf(UserInvalidRoleForSignupException.class);
        }

        @Test
        @DisplayName("잘못된 관리자 키 → UserInvalidAdminKeyException")
        void invalidAdminKey_throws() {
            var request = new UserAdminSignupRequest(
                    "adminuser", "관리자", "admin@test.com",
                    "010-9999-8888", "Password1!", "slack-admin",
                    true, UserRole.MANAGER, "wrong-key"
            );
            assertThatThrownBy(() -> userService.adminSignup(request))
                    .isInstanceOf(UserInvalidAdminKeyException.class);
        }
    }

    // ──────────────────────────────────────────────────
    // 로그인
    // ──────────────────────────────────────────────────
    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("성공 - 액세스/리프레시 토큰 반환")
        void success() {
            User user = activeUser();
            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
            given(keycloakService.login("testuser", "Password1!"))
                    .willReturn(new KeycloakTokenResponse("access-token", "refresh-token"));

            UserLoginResponse result = userService.login(new UserLoginRequest("testuser", "Password1!"));

            assertThat(result.accessToken()).isEqualTo("access-token");
            assertThat(result.refreshToken()).isEqualTo("refresh-token");
        }

        @Test
        @DisplayName("존재하지 않는 유저 → UserNotFoundException")
        void userNotFound_throws() {
            given(userRepository.findByUsername("nouser")).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.login(new UserLoginRequest("nouser", "pw")))
                    .isInstanceOf(UserNotFoundException.class);
        }

//        @Test
//        @DisplayName("정지된 유저 → UserSuspendedException")
//        void suspendedUser_throws() {
//            User user = activeUser();
//            user.suspendUser();
//            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));
//
//            assertThatThrownBy(() -> userService.login(new UserLoginRequest("testuser", "pw")))
//                    .isInstanceOf(UserSuspendedException.class);
//        }

        @Test
        @DisplayName("탈퇴한 유저 → UserDeletedException")
        void deletedUser_throws() {
            User user = activeUser();
            user.deleteUser();
            given(userRepository.findByUsername("testuser")).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.login(new UserLoginRequest("testuser", "pw")))
                    .isInstanceOf(UserDeletedException.class);
        }
    }

    // ──────────────────────────────────────────────────
    // 유저 조회 / 수정 / 탈퇴
    // ──────────────────────────────────────────────────
    @Nested
    @DisplayName("유저 조회 / 수정 / 탈퇴")
    class UserCrud {

        @Test
        @DisplayName("유저 조회 성공")
        void getUser_success() {
            UUID userId = UUID.randomUUID();
            User user = activeUser();
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            UserResponse result = userService.getUser(userId);

            assertThat(result.username()).isEqualTo("testuser");
            assertThat(result.status()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("유저 조회 - 없는 ID → UserNotFoundException")
        void getUser_notFound_throws() {
            UUID userId = UUID.randomUUID();
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUser(userId))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("프로필 수정 성공 - name, phone, slackId 변경됨")
        void updateUserInfo_success() {
            UUID userId = UUID.randomUUID();
            User user = activeUser();
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            UserResponse result = userService.updateUserInfo(
                    userId, new UserInfoUpdateRequest("새이름", "010-9999-9999", "new-slack"));

            assertThat(result.name()).isEqualTo("새이름");
        }

        @Test
        @DisplayName("탈퇴 성공 - 상태 DELETED 로 변경")
        void deleteUser_success() {
            UUID userId = UUID.randomUUID();
            User user = activeUser();
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            userService.deleteUser(userId);

            assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
            assertThat(user.isDeleted()).isTrue();
        }
    }

    // ──────────────────────────────────────────────────
    // 계정 정지 / 해제
    // ──────────────────────────────────────────────────
    @Nested
    @DisplayName("계정 정지 / 해제")
    class Suspension {

        @Test
        @DisplayName("정지 성공 - 상태 SUSPENDED, Redis Set에 userId 추가")
        void suspendUser_success() {
            UUID userId = UUID.randomUUID();
            User user = activeUser();
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(redisTemplate.opsForSet()).willReturn(setOperations);

            userService.suspendUser(new UserSuspendedRequest(userId));

            assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
            then(setOperations).should().add(eq("suspended:users"), eq(userId.toString()));
        }

        @Test
        @DisplayName("이미 정지된 유저 정지 → UserSuspendedException")
        void suspendUser_alreadySuspended_throws() {
            UUID userId = UUID.randomUUID();
            User user = activeUser();
            user.suspendUser();
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.suspendUser(new UserSuspendedRequest(userId)))
                    .isInstanceOf(UserSuspendedException.class);
        }

        @Test
        @DisplayName("탈퇴 유저 정지 시도 → UserDeletedException")
        void suspendUser_deletedUser_throws() {
            UUID userId = UUID.randomUUID();
            User user = activeUser();
            user.deleteUser();
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.suspendUser(new UserSuspendedRequest(userId)))
                    .isInstanceOf(UserDeletedException.class);
        }

        @Test
        @DisplayName("정지 해제 성공 - 상태 ACTIVE, Redis Set에서 userId 제거")
        void unsuspendUser_success() {
            UUID userId = UUID.randomUUID();
            User user = activeUser();
            user.suspendUser();
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(redisTemplate.opsForSet()).willReturn(setOperations);

            userService.unsuspendUser(new UserSuspendedRequest(userId));

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            then(setOperations).should().remove(eq("suspended:users"), eq((Object) userId.toString()));
        }

        @Test
        @DisplayName("정지 상태 아닌 유저 해제 → UserNotSuspendedException")
        void unsuspendUser_notSuspended_throws() {
            UUID userId = UUID.randomUUID();
            User user = activeUser();
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.unsuspendUser(new UserSuspendedRequest(userId)))
                    .isInstanceOf(UserNotSuspendedException.class);
        }
    }
}
