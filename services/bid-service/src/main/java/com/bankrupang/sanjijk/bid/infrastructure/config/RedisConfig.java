package com.bankrupang.sanjijk.bid.infrastructure.config;

import com.bankrupang.sanjijk.bid.infrastructure.redis.RedisBidSubscriber;
import io.netty.resolver.DefaultAddressResolverGroup;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Slf4j
@Configuration
public class RedisConfig {

    // [수정됨] lettuceConnectionFactory 빈 등록 삭제
    // 이유: 스프링 부트가 자동 생성하는 팩토리를 사용해야 내부 프록시 꼬임(StackOverflow)이 발생하지 않습니다.

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        // [수정됨] @Qualifier를 지우고, 스프링이 주는 기본 RedisConnectionFactory를 바로 사용합니다.
        return new StringRedisTemplate(redisConnectionFactory);
    }

    // RedissonAutoConfigurationV2가 Netty의 자체 DNS 리졸버를 사용하는데,
    // Docker 내부 DNS(127.0.0.11)와 호환 문제로 UnknownHostException이 발생함.
    // 직접 빈을 등록해서 JVM 기본 DNS 리졸버(DefaultAddressResolverGroup)를 사용하도록 강제.
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port);
        config.setAddressResolverGroupFactory(
                (c, sc, p) -> DefaultAddressResolverGroup.INSTANCE);
        return Redisson.create(config);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory, // [수정됨] 기본 팩토리 사용
            RedisBidSubscriber redisBidSubscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        // 연결 끊김 후 재구독 시도 간격 (ms)
        container.setRecoveryInterval(3000L);
        // onMessage 예외 또는 연결 복구 실패 시 로그로 확인
        container.setErrorHandler(e -> log.error("[RedisMessageListener] 에러 발생 - 재연결 시도됩니다", e));
        container.addMessageListener(redisBidSubscriber, new PatternTopic("auction:*:bid-event"));
        return container;
    }
}