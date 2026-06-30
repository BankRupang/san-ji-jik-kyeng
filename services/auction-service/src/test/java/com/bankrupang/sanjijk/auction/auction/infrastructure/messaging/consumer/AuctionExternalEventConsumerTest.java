package com.bankrupang.sanjijk.auction.auction.infrastructure.messaging.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.bankrupang.sanjijk.auction.auction.application.service.AuctionService;
import com.bankrupang.sanjijk.auction.auction.infrastructure.messaging.consumer.dto.AuctionEndedEvent;
import com.bankrupang.sanjijk.auction.auction.infrastructure.messaging.consumer.dto.DepositForfeitedEvent;
import com.bankrupang.sanjijk.auction.auction.infrastructure.messaging.consumer.dto.PaymentCompletedEvent;
import com.bankrupang.sanjijk.auction.auction.infrastructure.messaging.consumer.dto.PaymentFailedEvent;

@DisplayName("AuctionExternalEventConsumer 테스트")
@ExtendWith(MockitoExtension.class)
class AuctionExternalEventConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Mock
    private AuctionService auctionService;

    private AuctionExternalEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AuctionExternalEventConsumer(objectMapper, auctionService);
    }

    @Test
    @DisplayName("AUCTION_ENDED payload를 매핑하고 경매 종료 처리를 위임한다")
    void consumeAuctionEnded() throws Exception {
        // given
        UUID auctionId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        AuctionEndedEvent event = new AuctionEndedEvent(
                auctionId,
                true,
                winnerId,
                15000,
                LocalDateTime.now()
        );

        // when
        consumer.consumeAuctionEnded(objectMapper.writeValueAsString(event));

        // then
        verify(auctionService).closeAuctionByEndedEvent(auctionId, true, winnerId, 15000);
    }

    @Test
    @DisplayName("PAYMENT_COMPLETED payload를 매핑하고 결제 완료 처리를 위임한다")
    void consumePaymentCompleted() throws Exception {
        // given
        UUID auctionId = UUID.randomUUID();
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.randomUUID(),
                auctionId,
                "사과 경매",
                UUID.randomUUID(),
                UUID.randomUUID(),
                15000,
                15000,
                "FINAL_PAYMENT",
                LocalDateTime.now()
        );

        // when
        consumer.consumePaymentCompleted(objectMapper.writeValueAsString(event));

        // then
        verify(auctionService).completeAuctionPayment(auctionId);
    }

    @Test
    @DisplayName("PAYMENT_FAILED payload를 매핑하고 결제 실패 처리를 위임한다")
    void consumePaymentFailed() throws Exception {
        // given
        UUID auctionId = UUID.randomUUID();
        PaymentFailedEvent event = new PaymentFailedEvent(
                UUID.randomUUID(),
                auctionId,
                "사과 경매",
                UUID.randomUUID(),
                UUID.randomUUID(),
                15000,
                "카드 승인 실패",
                LocalDateTime.now()
        );

        // when
        consumer.consumePaymentFailed(objectMapper.writeValueAsString(event));

        // then
        verify(auctionService).failAuctionPayment(auctionId);
    }

    @Test
    @DisplayName("DEPOSIT_FORFEITED payload를 매핑하고 결제 실패 처리를 위임한다")
    void consumeDepositForfeited() throws Exception {
        // given
        UUID auctionId = UUID.randomUUID();
        DepositForfeitedEvent event = new DepositForfeitedEvent(
                UUID.randomUUID(),
                auctionId,
                "사과 경매",
                UUID.randomUUID(),
                UUID.randomUUID(),
                10000,
                LocalDateTime.now()
        );

        // when
        consumer.consumeDepositForfeited(objectMapper.writeValueAsString(event));

        // then
        verify(auctionService).failAuctionPayment(auctionId);
    }

    @Test
    @DisplayName("잘못된 JSON payload는 예외를 발생시킨다")
    void fail_invalid_payload() {
        // when & then
        assertThatThrownBy(() -> consumer.consumeAuctionEnded("{invalid-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AUCTION_ENDED payload 역직렬화에 실패했습니다.");
    }
}
