package com.bankrupang.sanjijk.auction.outbox.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.outbox.domain.entity.AuctionOutbox;
import com.bankrupang.sanjijk.auction.outbox.domain.repository.AuctionOutboxRepository;
import com.bankrupang.sanjijk.auction.outbox.domain.type.AuctionEventType;
import com.bankrupang.sanjijk.auction.product.domain.entity.Product;

@DisplayName("AuctionOutboxService 테스트")
@ExtendWith(MockitoExtension.class)
class AuctionOutboxServiceTest {

    @InjectMocks
    private AuctionOutboxService auctionOutboxService;

    @Mock
    private AuctionOutboxRepository auctionOutboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Nested
    @DisplayName("saveAuctionStartEvent()")
    class SaveAuctionStartEvent {

        @Test
        @DisplayName("성공 - 경매 시작 이벤트를 Outbox에 저장한다")
        void success() throws Exception {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Product product = createProduct(sellerId, productId);
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();

            ReflectionTestUtils.setField(auctionOutboxService, "objectMapper", objectMapper);

            // when
            auctionOutboxService.saveAuctionStartEvent(auction, product);

            // then
            AuctionOutbox outbox = captureSavedOutbox();
            JsonNode payload = objectMapper.readTree(outbox.getPayload());

            assertThat(outbox.getAggregateType()).isEqualTo("AUCTION");
            assertThat(outbox.getAggregateId()).isEqualTo(auctionId);
            assertThat(outbox.getEventType()).isEqualTo(AuctionEventType.AUCTION_START);
            assertThat(outbox.isPublished()).isFalse();
            assertThat(outbox.getPublishedAt()).isNull();
            assertThat(payload.get("auctionId").asText()).isEqualTo(auctionId.toString());
            assertThat(payload.get("sellerId").asText()).isEqualTo(sellerId.toString());
            assertThat(payload.get("productName").asText()).isEqualTo(product.getName());
            assertThat(payload.get("startPrice").asInt()).isEqualTo(auction.getStartPrice());
            assertThat(payload.get("bidUnit").asInt()).isEqualTo(auction.getBidUnit());
            assertThat(payload.get("status").asText()).isEqualTo("PROGRESS");
        }
    }

    @Nested
    @DisplayName("saveAuctionWonEvent()")
    class SaveAuctionWonEvent {

        @Test
        @DisplayName("성공 - 낙찰 이벤트를 Outbox에 저장한다")
        void success() throws Exception {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            UUID winnerId = UUID.randomUUID();
            Product product = createProduct(sellerId, productId);
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();
            auction.markResultPending();
            auction.markWon(winnerId, 15000);

            ReflectionTestUtils.setField(auctionOutboxService, "objectMapper", objectMapper);

            // when
            auctionOutboxService.saveAuctionWonEvent(auction, product);

            // then
            AuctionOutbox outbox = captureSavedOutbox();
            JsonNode payload = objectMapper.readTree(outbox.getPayload());

            assertThat(outbox.getAggregateId()).isEqualTo(auctionId);
            assertThat(outbox.getEventType()).isEqualTo(AuctionEventType.AUCTION_WON);
            assertThat(outbox.isPublished()).isFalse();
            assertThat(payload.get("eventType").asText()).isEqualTo("AUCTION_WON");
            assertThat(payload.get("auctionId").asText()).isEqualTo(auctionId.toString());
            assertThat(payload.get("auctionTitle").asText()).isEqualTo(product.getName());
            assertThat(payload.get("winnerId").asText()).isEqualTo(winnerId.toString());
            assertThat(payload.get("sellerId").asText()).isEqualTo(sellerId.toString());
            assertThat(payload.get("finalPrice").asInt()).isEqualTo(15000);
            assertThat(payload.get("depositAmount").asInt()).isEqualTo(auction.getStartPrice());
            assertThat(payload.get("occurredAt")).isNotNull();
        }
    }

    @Nested
    @DisplayName("saveAuctionFailedEvent()")
    class SaveAuctionFailedEvent {

        @Test
        @DisplayName("성공 - 유찰 이벤트를 Outbox에 저장한다")
        void success() throws Exception {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Product product = createProduct(sellerId, productId);
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();
            auction.markResultPending();
            auction.markFailed();

            ReflectionTestUtils.setField(auctionOutboxService, "objectMapper", objectMapper);

            // when
            auctionOutboxService.saveAuctionFailedEvent(auction, product);

            // then
            AuctionOutbox outbox = captureSavedOutbox();
            JsonNode payload = objectMapper.readTree(outbox.getPayload());

            assertThat(outbox.getAggregateId()).isEqualTo(auctionId);
            assertThat(outbox.getEventType()).isEqualTo(AuctionEventType.AUCTION_FAILED);
            assertThat(outbox.isPublished()).isFalse();
            assertThat(payload.get("eventType").asText()).isEqualTo("AUCTION_FAILED");
            assertThat(payload.get("auctionId").asText()).isEqualTo(auctionId.toString());
            assertThat(payload.get("auctionTitle").asText()).isEqualTo(product.getName());
            assertThat(payload.get("sellerId").asText()).isEqualTo(sellerId.toString());
            assertThat(payload.get("occurredAt")).isNotNull();
        }
    }

    private AuctionOutbox captureSavedOutbox() {
        ArgumentCaptor<AuctionOutbox> outboxCaptor = ArgumentCaptor.forClass(AuctionOutbox.class);
        verify(auctionOutboxRepository).save(outboxCaptor.capture());
        return outboxCaptor.getValue();
    }

    private Product createProduct(UUID sellerId, UUID productId) {
        Product product = Product.create(
                sellerId,
                "사과",
                "신선한 사과입니다.",
                "10"
        );
        ReflectionTestUtils.setField(product, "id", productId);
        return product;
    }

    private Auction createAuction(UUID sellerId, UUID productId, UUID auctionId) {
        LocalDateTime startAt = LocalDateTime.now().plusDays(1);
        Auction auction = Auction.create(
                productId,
                sellerId,
                10000,
                1000,
                startAt,
                startAt.plusHours(1)
        );
        ReflectionTestUtils.setField(auction, "id", auctionId);
        return auction;
    }
}
