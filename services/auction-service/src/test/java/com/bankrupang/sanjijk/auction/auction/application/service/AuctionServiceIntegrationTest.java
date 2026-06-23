package com.bankrupang.sanjijk.auction.auction.application.service;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.repository.AuctionRepository;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.request.AuctionCreateRequest;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionCreateResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionStartResponse;
import com.bankrupang.sanjijk.auction.outbox.domain.entity.AuctionOutbox;
import com.bankrupang.sanjijk.auction.outbox.domain.repository.AuctionOutboxRepository;
import com.bankrupang.sanjijk.auction.outbox.domain.type.AuctionEventType;
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
}
