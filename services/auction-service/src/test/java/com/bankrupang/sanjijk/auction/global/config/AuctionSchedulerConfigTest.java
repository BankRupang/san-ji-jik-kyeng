package com.bankrupang.sanjijk.auction.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@DisplayName("AuctionSchedulerConfig 테스트")
class AuctionSchedulerConfigTest {

    @Test
    @DisplayName("성공 - 경매 스케줄러 설정을 생성한다")
    void success() {
        // given
        AuctionSchedulerConfig config = new AuctionSchedulerConfig();

        // when
        ThreadPoolTaskScheduler scheduler = config.auctionTaskScheduler();

        // then
        try {
            assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(4);
            assertThat(scheduler.getThreadNamePrefix()).isEqualTo("auction-scheduler-");
        } finally {
            scheduler.shutdown();
        }
    }
}
