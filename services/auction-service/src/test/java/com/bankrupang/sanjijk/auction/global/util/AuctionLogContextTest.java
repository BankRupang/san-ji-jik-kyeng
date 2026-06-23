package com.bankrupang.sanjijk.auction.global.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

@DisplayName("AuctionLogContext 테스트")
class AuctionLogContextTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("auctionId를 MDC에 설정하고 작업 후 제거한다")
    void runWithAuctionId() {
        // given
        UUID auctionId = UUID.randomUUID();

        // when
        AuctionLogContext.runWithAuctionId(auctionId, () ->
                assertThat(MDC.get("auctionId")).isEqualTo(auctionId.toString()));

        // then
        assertThat(MDC.get("auctionId")).isNull();
    }

    @Test
    @DisplayName("기존 auctionId가 있으면 작업 후 이전 값으로 복원한다")
    void restorePreviousAuctionId() {
        // given
        UUID previousAuctionId = UUID.randomUUID();
        UUID auctionId = UUID.randomUUID();
        MDC.put("auctionId", previousAuctionId.toString());

        // when
        AuctionLogContext.runWithAuctionId(auctionId, () ->
                assertThat(MDC.get("auctionId")).isEqualTo(auctionId.toString()));

        // then
        assertThat(MDC.get("auctionId")).isEqualTo(previousAuctionId.toString());
    }
}
