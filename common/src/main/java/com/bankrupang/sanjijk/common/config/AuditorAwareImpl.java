package com.bankrupang.sanjijk.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class AuditorAwareImpl implements AuditorAware<UUID> {

    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public Optional<UUID> getCurrentAuditor() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            String userId = attrs.getRequest().getHeader(USER_ID_HEADER);
            if (userId == null) return Optional.empty();
            return Optional.of(UUID.fromString(userId));
        } catch (IllegalStateException | IllegalArgumentException e) {
            log.warn("[AuditorAware] userId 추출 실패: {}", e.getMessage());
            return Optional.empty();
        }
    }
}