package com.bankrupang.sanjijk.auction.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@DisplayName("LoggingShedLockProvider 테스트")
@ExtendWith(MockitoExtension.class)
class LoggingShedLockProviderTest {

    @Mock
    private LockProvider delegate;

    @Mock
    private SimpleLock simpleLock;

    @Test
    @DisplayName("성공 - 락을 획득하면 SimpleLock을 반환하고 unlock을 위임한다")
    void success_lock_acquired() {
        // given
        LockConfiguration lockConfiguration = createLockConfiguration();
        LoggingShedLockProvider lockProvider = new LoggingShedLockProvider(delegate);

        given(delegate.lock(lockConfiguration)).willReturn(Optional.of(simpleLock));

        // when
        Optional<SimpleLock> result = lockProvider.lock(lockConfiguration);

        // then
        assertThat(result).isPresent();

        result.get().unlock();
        verify(simpleLock).unlock();
    }

    @Test
    @DisplayName("성공 - 락을 획득하지 못하면 empty를 반환한다")
    void success_lock_skipped() {
        // given
        LockConfiguration lockConfiguration = createLockConfiguration();
        LoggingShedLockProvider lockProvider = new LoggingShedLockProvider(delegate);

        given(delegate.lock(lockConfiguration)).willReturn(Optional.empty());

        // when
        Optional<SimpleLock> result = lockProvider.lock(lockConfiguration);

        // then
        assertThat(result).isEmpty();
    }

    private LockConfiguration createLockConfiguration() {
        return new LockConfiguration(
                Instant.now(),
                "auction-start-1",
                Duration.ofMinutes(10),
                Duration.ofSeconds(1)
        );
    }
}
