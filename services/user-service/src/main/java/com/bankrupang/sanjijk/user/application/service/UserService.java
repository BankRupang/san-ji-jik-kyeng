
package com.bankrupang.sanjijk.user.application.service;

import com.bankrupang.sanjijk.user.infrastructure.keycloak.KeycloakService;
import com.bankrupang.sanjijk.user.domain.entity.User;
import com.bankrupang.sanjijk.user.domain.repository.UserRepository;
import com.bankrupang.sanjijk.user.presentation.dto.request.UserSignupRequest;
import com.bankrupang.sanjijk.user.presentation.dto.response.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final KeycloakService keycloakService;

    // 회원가입
    @Transactional
    public UserResponse signup(UserSignupRequest request) {
        validUser(request);

        UUID keycloakId = keycloakService.createUser(
                request.username(),
                request.email(),
                request.password()
        );

        try {
            User user = createUser(
                    keycloakId,
                    request
            );
            User savedUser = userRepository.save(user);
            return UserResponse.from(savedUser);
        } catch (Exception exception) {
            keycloakService.deleteUser(keycloakId);
            throw new RuntimeException("DB 저장 실패로 가입이 취소 되었습니다");
        }


    }

    // 가입시 검증로직
    private void validUser(UserSignupRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Username is already in use");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (userRepository.existsByBusinessNumber(request.businessNumber())) {
            throw new IllegalArgumentException("Business number already exists");
        }
    }

    // 유저 생성 메소드
    private User createUser(UUID userId, UserSignupRequest request) {
        return User.create(
                userId,
                request.username(),
                request.name(),
                request.email(),
                request.phone(),
                request.businessNumber(),
                request.slackId(),
                request.notificationAllow(),
                request.role()
        );
    }
}
