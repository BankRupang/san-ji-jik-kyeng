package com.bankrupang.sanjijk.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JWT 검증 후 claims를 X-User-Id, X-User-Role 헤더로 변환해 내부 서비스에 전달.
 * 클라이언트가 직접 심은 X-User-* 헤더는 제거해 스푸핑을 방지한다.
 */
@Component
public class JwtClaimsInjectionFilter implements GlobalFilter, Ordered {

    // Keycloak 내장 role — 도메인 권한 아니므로 제외
    private static final Set<String> KEYCLOAK_SYSTEM_ROLES = Set.of(
            "offline_access", "uma_authorization"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(auth -> {
                    var jwt = auth.getToken();
                    String userId = jwt.getSubject();
                    String role = extractRole(jwt.getClaimAsMap("realm_access"));

                    ServerWebExchange mutated = exchange.mutate()
                            .request(r -> r.headers(headers -> {
                                removeUserHeaders(headers);
                                if (userId != null) headers.set("X-User-Id", userId);
                                if (role != null)   headers.set("X-User-Role", role);
                            }))
                            .build();
                    return chain.filter(mutated);
                })
                .switchIfEmpty(
                        // 비인증 요청(공개 엔드포인트)은 X-User-* 헤더만 제거 후 통과
                        chain.filter(exchange.mutate()
                                .request(r -> r.headers(this::removeUserHeaders))
                                .build())
                );
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private void removeUserHeaders(org.springframework.http.HttpHeaders headers) {
        List.copyOf(headers.keySet()).stream()
                .filter(name -> name.regionMatches(true, 0, "X-User-", 0, 7))
                .forEach(headers::remove);
    }

    @SuppressWarnings("unchecked")
    private String extractRole(Map<String, Object> realmAccess) {
        if (realmAccess == null) return null;
        List<String> roles = (List<String>) realmAccess.get("roles");
        if (roles == null) return null;
        return roles.stream()
                .filter(r -> !r.startsWith("default-roles-") && !KEYCLOAK_SYSTEM_ROLES.contains(r))
                .findFirst()
                .orElse(null);
    }
}
