package com.bankrupang.sanjijk.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * JWT 검증 후 claims를 X-User-Id, X-User-Role 헤더로 변환해 내부 서비스에 전달.
 * 클라이언트가 직접 심은 X-User-* 헤더는 제거해 스푸핑을 방지한다.
 */
@Slf4j
@Component
public class JwtClaimsInjectionFilter implements GlobalFilter, Ordered {

    // Keycloak 내장 role — 도메인 권한 아니므로 제외
    private static final Set<String> KEYCLOAK_SYSTEM_ROLES = Set.of(
            "offline_access", "uma_authorization"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String method = req.getMethod().name();
        String path   = req.getURI().getRawPath();

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .flatMap(auth -> {
                    var jwt = auth.getToken();
                    String userId = jwt.getSubject();
                    List<String> roles = jwt.getClaimAsStringList("role");
                    String role = (roles != null && !roles.isEmpty()) ? roles.get(0) : null;

                    log.debug("[GW] {} {} | userId={} role={}", method, path, userId, role);

                    if (role == null) {
                        log.warn("[GW] JWT에 role 클레임 없음 — userId={} path={}", userId, path);
                    }

                    ServerWebExchange mutated = exchange.mutate()
                            .request(r -> r.headers(headers -> {
                                removeUserHeaders(headers);
                                if (userId != null) headers.set("X-User-Id", userId);
                                if (role != null)   headers.set("X-User-Role", role);
                            }))
                            .build();

                    return chain.filter(mutated)
                            .doOnSuccess(v -> {
                                ServerHttpResponse res = mutated.getResponse();
                                int status = res.getStatusCode() != null ? res.getStatusCode().value() : 0;
                                if (status >= 400) {
                                    log.warn("[GW] {} {} → {} | userId={} role={}", method, path, status, userId, role);
                                } else {
                                    log.debug("[GW] {} {} → {} | userId={} role={}", method, path, status, userId, role);
                                }
                            });
                })
                .switchIfEmpty(
                        // 비인증 요청(공개 엔드포인트)은 X-User-* 헤더만 제거 후 통과
                        Mono.defer(() -> {
                            log.debug("[GW] {} {} | 비인증 요청", method, path);
                            ServerWebExchange mutated = exchange.mutate()
                                    .request(r -> r.headers(this::removeUserHeaders))
                                    .build();
                            return chain.filter(mutated)
                                    .doOnSuccess(v -> {
                                        ServerHttpResponse res = mutated.getResponse();
                                        int status = res.getStatusCode() != null ? res.getStatusCode().value() : 0;
                                        if (status >= 400) {
                                            log.warn("[GW] {} {} → {} | 비인증", method, path, status);
                                        }
                                    });
                        })
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
}
