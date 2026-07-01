package com.bankrupang.sanjijk.bid.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String SUSPENDED_USERS_KEY = "suspended:users";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/bid")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        // native WebSocket (SockJS 없이 직접 연결용)
        registry.addEndpoint("/ws/bid-native")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String userId = accessor.getFirstNativeHeader("X-User-Id");
                    log.info("STOMP CONNECT 인터셉트 - X-User-Id: {}", userId);

                    if (userId == null) {
                        throw new MessagingException("X-User-Id 헤더가 없습니다.");
                    }

                    Boolean isSuspended = redisTemplate.opsForSet()
                            .isMember(SUSPENDED_USERS_KEY, userId);
                    if (Boolean.TRUE.equals(isSuspended)) {
                        log.warn("정지된 유저 접속 시도 차단 - userId: {}", userId);
                        throw new MessagingException("정지된 사용자는 입찰에 참여할 수 없습니다.");
                    }

                    String role = accessor.getFirstNativeHeader("X-User-Role");
                    accessor.setUser(new UserPrincipal(userId, role));
                    log.info("Principal 설정 완료 - userId: {}", userId);
                }
                return message;
            }
        });
    }
}
