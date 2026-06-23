package com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    // 최대 3회 재시도, 재시도 간격 1초
    private static final long RETRY_INTERVAL_MS = 1000L;
    private static final long MAX_RETRY_COUNT = 3L;

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // 처리 실패 시 {topic}.DLT 토픽으로 발행
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRY_COUNT)
        );

        // 재시도 시작 로그
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("[KAFKA] 메시지 처리 재시도 - topic: {}, attempt: {}, error: {}",
                        record.topic(), deliveryAttempt, ex.getMessage())
        );

        return errorHandler;
    }
}
