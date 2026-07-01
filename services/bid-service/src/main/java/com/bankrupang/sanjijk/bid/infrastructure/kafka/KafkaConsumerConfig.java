package com.bankrupang.sanjijk.bid.infrastructure.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    private static final long RETRY_INTERVAL_MS = 1000L;
    private static final long MAX_RETRY_COUNT = 3L;

    @Bean
    public DefaultErrorHandler errorHandler() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                (record, ex) -> log.error("[BID-KAFKA] 처리 실패 메시지 스킵 - topic: {}, partition: {}, offset: {}, error: {}",
                        record.topic(), record.partition(), record.offset(), ex.getMessage()),
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRY_COUNT)
        );

        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("[BID-KAFKA] 재시도 - topic: {}, attempt: {}, error: {}",
                        record.topic(), deliveryAttempt, ex.getMessage())
        );

        return errorHandler;
    }
}
