package com.bankrupang.sanjijk.auction.global.config;

import java.time.Duration;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

@Slf4j
@RequiredArgsConstructor
class LoggingShedLockProvider implements LockProvider {

    private final LockProvider delegate;

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        Optional<SimpleLock> lock = delegate.lock(lockConfiguration);
        String lockName = lockConfiguration.getName();

        if (lock.isPresent()) {
            log.info("ShedLock 획득 - lockName: {}, lockAtMostUntil: {}, lockAtLeastUntil: {}",
                    lockName, lockConfiguration.getLockAtMostUntil(), lockConfiguration.getLockAtLeastUntil());
            return lock.map(simpleLock -> new LoggingSimpleLock(simpleLock, lockName));
        }

        log.info("ShedLock 미획득 - 다른 인스턴스에서 실행 중입니다. lockName: {}", lockName);
        return Optional.empty();
    }

    @RequiredArgsConstructor
    private static class LoggingSimpleLock implements SimpleLock {

        private final SimpleLock delegate;
        private final String lockName;

        @Override
        public void unlock() {
            delegate.unlock();
            log.info("ShedLock 해제 - lockName: {}", lockName);
        }

        @Override
        public Optional<SimpleLock> extend(Duration lockAtMostFor, Duration lockAtLeastFor) {
            return delegate.extend(lockAtMostFor, lockAtLeastFor)
                    .map(extendedLock -> new LoggingSimpleLock(extendedLock, lockName));
        }
    }
}
