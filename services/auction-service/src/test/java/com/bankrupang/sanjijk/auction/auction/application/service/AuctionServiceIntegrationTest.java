package com.bankrupang.sanjijk.auction.auction.application.service;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.repository.AuctionRepository;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.request.AuctionCancelRequest;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.request.AuctionCloseRequest;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.request.AuctionCreateRequest;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionCancelResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionCloseResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionCreateResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionStartResponse;
import com.bankrupang.sanjijk.auction.outbox.domain.entity.AuctionOutbox;
import com.bankrupang.sanjijk.auction.outbox.domain.repository.AuctionOutboxRepository;
import com.bankrupang.sanjijk.auction.outbox.domain.type.AuctionEventType;
import com.bankrupang.sanjijk.auction.auction.infrastructure.client.BidClient;
import com.bankrupang.sanjijk.auction.product.domain.entity.Product;
import com.bankrupang.sanjijk.auction.product.domain.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.autoconfigure.exclude=")
@ActiveProfiles("test")
@Transactional
@DisplayName("AuctionService 통합 테스트 (H2 연동)")
class AuctionServiceIntegrationTest {

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AuctionOutboxRepository auctionOutboxRepository;

    @org.springframework.boot.test.mock.mockito.MockBean
    private BidClient bidClient;

    private UUID sellerId;
    private Product product;

    @BeforeEach
    void setUp() {
        sellerId = UUID.randomUUID();
        product = Product.create(
                sellerId,
                "통합 테스트 수박",
                "로컬 데이터베이스 연동 테스트용 당도 보장 수박",
                "100"
        );
        productRepository.save(product);
    }

    @Test
    @DisplayName("경매 생성 통합 테스트 - 데이터베이스에 경매 및 상품 관계가 정상적으로 연동 저장된다")
    void createAuction_integration() {
        // given
        LocalDateTime startAt = LocalDateTime.now().plusDays(1);
        AuctionCreateRequest request = new AuctionCreateRequest(
                product.getId(),
                10000,
                1000,
                startAt
        );

        // when
        AuctionCreateResponse response = auctionService.createAuction(sellerId, "SELLER", request);

        // then
        assertThat(response.auctionId()).isNotNull();
        assertThat(response.status()).isEqualTo(AuctionStatus.READY);

        // DB에 실제로 영속화되었는지 데이터 조회 및 검증
        Auction savedAuction = auctionRepository.findById(response.auctionId()).orElse(null);
        assertThat(savedAuction).isNotNull();
        assertThat(savedAuction.getProductId()).isEqualTo(product.getId());
        assertThat(savedAuction.getStartPrice()).isEqualTo(10000);
        assertThat(savedAuction.getBidUnit()).isEqualTo(1000);
    }

    @Test
    @DisplayName("수동 경매 시작 통합 테스트 - 경매 상태가 PROGRESS로 갱신되고 Outbox 테이블에 AUCTION_START 이벤트가 저장된다")
    void startAuctionManually_integration() {
        // given
        LocalDateTime startAt = LocalDateTime.now().plusHours(1);
        Auction auction = Auction.create(
                product.getId(),
                sellerId,
                15000,
                1000,
                startAt,
                startAt.plusHours(1)
        );
        auctionRepository.save(auction);

        // when
        AuctionStartResponse response = auctionService.startAuctionManually(auction.getId());

        // then
        assertThat(response.status()).isEqualTo(AuctionStatus.PROGRESS);

        // DB 상태 업데이트 및 낙찰 관련 데이터 검증
        Auction updatedAuction = auctionRepository.findById(auction.getId()).orElse(null);
        assertThat(updatedAuction).isNotNull();
        assertThat(updatedAuction.getStatus()).isEqualTo(AuctionStatus.PROGRESS);

        // Outbox 테이블에 메시지가 등록되었는지 확인
        List<AuctionOutbox> outboxes = auctionOutboxRepository.findAll();
        assertThat(outboxes).isNotEmpty();
        boolean hasStartEvent = outboxes.stream()
                .anyMatch(o -> AuctionEventType.AUCTION_START.equals(o.getEventType()) && o.getAggregateId().equals(auction.getId()));
        assertThat(hasStartEvent).isTrue();
    }

    @Test
    @DisplayName("수동 경매 마감 통합 테스트 - 경매 상태가 WON으로 갱신되고 Outbox 테이블에 AUCTION_WON 이벤트가 저장된다")
    void closeAuctionManually_integration() {
        // given
        LocalDateTime startAt = LocalDateTime.now().minusHours(1);
        Auction auction = Auction.create(
                product.getId(),
                sellerId,
                15000,
                1000,
                startAt,
                startAt.plusHours(1)
        );
        auction.start();
        auctionRepository.save(auction);

        UUID winnerId = UUID.randomUUID();
        AuctionCloseRequest request = new AuctionCloseRequest(false);
        org.mockito.BDDMockito.given(bidClient.getHighestBid(auction.getId()))
                .willReturn(new com.bankrupang.sanjijk.auction.auction.infrastructure.client.dto.HighestBidResponse(winnerId, 20000));

        // when
        AuctionCloseResponse response = auctionService.closeAuctionManually(auction.getId(), request);

        // then
        assertThat(response.status()).isEqualTo(AuctionStatus.WON);
        assertThat(response.winnerId()).isEqualTo(winnerId);
        assertThat(response.finalPrice()).isEqualTo(20000);

        // DB 상태 업데이트 검증
        Auction updatedAuction = auctionRepository.findById(auction.getId()).orElse(null);
        assertThat(updatedAuction).isNotNull();
        assertThat(updatedAuction.getStatus()).isEqualTo(AuctionStatus.WON);
        assertThat(updatedAuction.getWinnerId()).isEqualTo(winnerId);
        assertThat(updatedAuction.getFinalPrice()).isEqualTo(20000);

        // Outbox 검증
        List<AuctionOutbox> outboxes = auctionOutboxRepository.findAll();
        assertThat(outboxes).isNotEmpty();
        boolean hasWonEvent = outboxes.stream()
                .anyMatch(o -> AuctionEventType.AUCTION_WON.equals(o.getEventType()) && o.getAggregateId().equals(auction.getId()));
        assertThat(hasWonEvent).isTrue();
    }

    @Test
    @DisplayName("경매 취소 통합 테스트 - READY 상태의 경매를 취소하면 CANCELLED 상태로 정상 변경된다")
    void cancelAuction_integration() {
        // given
        LocalDateTime startAt = LocalDateTime.now().plusDays(2);
        Auction auction = Auction.create(
                product.getId(),
                sellerId,
                10000,
                1000,
                startAt,
                startAt.plusHours(2)
        );
        auctionRepository.save(auction);

        AuctionCancelRequest request = new AuctionCancelRequest("단순 변심으로 인한 취소");

        // when
        AuctionCancelResponse response = auctionService.cancelAuction(sellerId, "SELLER", auction.getId(), request);

        // then
        assertThat(response.status()).isEqualTo(AuctionStatus.CANCELLED);
        assertThat(response.reason()).isEqualTo("단순 변심으로 인한 취소");

        // DB 상태 업데이트 검증
        Auction updatedAuction = auctionRepository.findById(auction.getId()).orElse(null);
        assertThat(updatedAuction).isNotNull();
        assertThat(updatedAuction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        assertThat(updatedAuction.getCancelReason()).isEqualTo("단순 변심으로 인한 취소");
    }
}
