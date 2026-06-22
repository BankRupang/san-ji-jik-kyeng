package com.bankrupang.sanjijk.user.infrastructure.config;

import com.bankrupang.sanjijk.user.domain.UserStatus;
import com.bankrupang.sanjijk.user.domain.entity.User;
import com.bankrupang.sanjijk.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SuspendedUserCacheInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<User> suspendedUsers = userRepository.findAllByStatus(UserStatus.SUSPENDED);

        if (suspendedUsers.isEmpty()) {
            log.info("[Redis] 정지된 유저 없음, 캐시 초기화 생략");
            return;
        }

        String[] userIds = suspendedUsers.stream()
                .map(user -> user.getId().toString())
                .toArray(String[]::new);

        redisTemplate.opsForSet().add(RedisKeys.SUSPENDED_USERS, userIds);
        log.info("[Redis] 정지 유저 {}명 캐시 동기화 완료", suspendedUsers.size());
    }
}
