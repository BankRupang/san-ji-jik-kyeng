package com.bankrupang.sanjijk.bid.infrastructure.config;

import com.bankrupang.sanjijk.bid.infrastructure.redis.RedisBidSubscriber;
import io.netty.resolver.DefaultAddressResolverGroup;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {

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
            RedisConnectionFactory connectionFactory,
            RedisBidSubscriber redisBidSubscriber
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(redisBidSubscriber, new PatternTopic("auction:*:bid-event"));
        return container;
    }
}
