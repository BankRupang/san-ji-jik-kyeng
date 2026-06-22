package com.bankrupang.sanjijk.auction.auction.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.repository.AuctionRepository;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.auction.outbox.application.service.AuctionOutboxService;
import com.bankrupang.sanjijk.auction.product.domain.entity.Product;
import com.bankrupang.sanjijk.auction.product.domain.repository.ProductRepository;

@DisplayName("AuctionSchedulerJobService 테스트")
@ExtendWith(MockitoExtension.class)
class AuctionSchedulerJobServiceTest {

    @InjectMocks
    private AuctionSchedulerJobService auctionSchedulerJobService;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AuctionOutboxService auctionOutboxService;

    @Mock
    private AuctionScheduleManager auctionScheduleManager;

    @Mock
    private ObjectProvider<AuctionSchedulerJobService> schedulerJobServiceProvider;

    @Nested
    @DisplayName("startAuction()")
    class StartAuction {

        @Test
        @DisplayName("성공 - READY 경매를 PROGRESS로 전환하고 시작 이벤트를 저장한다")
        void success() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            Product product = createProduct(sellerId, productId);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));
            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));

            // when
            auctionSchedulerJobService.startAuction(auctionId);

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PROGRESS);
            verify(auctionOutboxService).saveAuctionStartEvent(auction, product);
            verify(auctionScheduleManager).scheduleEndCheckJob(any(UUID.class), any(LocalDateTime.class), any(Runnable.class));
        }

        @Test
        @DisplayName("성공 - 시작 후 등록한 마감 확인 잡은 프록시를 통해 실행한다")
        void success_schedule_end_check_job_via_proxy() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            Product product = createProduct(sellerId, productId);
            AuctionSchedulerJobService proxiedJobService = mock(AuctionSchedulerJobService.class);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));
            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));
            given(schedulerJobServiceProvider.getObject()).willReturn(proxiedJobService);

            // when
            auctionSchedulerJobService.startAuction(auctionId);

            // then
            ArgumentCaptor<Runnable> jobCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(auctionScheduleManager).scheduleEndCheckJob(any(UUID.class), any(LocalDateTime.class), jobCaptor.capture());

            jobCaptor.getValue().run();
            verify(proxiedJobService).checkAuctionEnd(auctionId);
        }

        @Test
        @DisplayName("성공 - READY 상태가 아니면 시작하지 않는다")
        void success_skip_not_ready() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.cancel("취소");

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when
            auctionSchedulerJobService.startAuction(auctionId);

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
            verify(productRepository, never()).findByIdAndDeletedAtIsNull(any(UUID.class));
            verify(auctionOutboxService, never()).saveAuctionStartEvent(any(Auction.class), any(Product.class));
            verify(auctionScheduleManager, never()).scheduleEndCheckJob(any(UUID.class), any(LocalDateTime.class), any(Runnable.class));
        }

        @Test
        @DisplayName("성공 - 상품이 존재하지 않으면 시작하지 않는다")
        void success_skip_missing_product() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));
            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.empty());

            // when
            auctionSchedulerJobService.startAuction(auctionId);

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.READY);
            verify(auctionOutboxService, never()).saveAuctionStartEvent(any(Auction.class), any(Product.class));
            verify(auctionScheduleManager, never()).scheduleEndCheckJob(any(UUID.class), any(LocalDateTime.class), any(Runnable.class));
        }
    }

    @Nested
    @DisplayName("checkAuctionEnd()")
    class CheckAuctionEnd {

        @Test
        @DisplayName("성공 - PROGRESS 경매의 마감 시각 도달 여부를 확인한다")
        void success_progress() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when
            auctionSchedulerJobService.checkAuctionEnd(auctionId);

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PROGRESS);
            verify(auctionOutboxService, never()).saveAuctionFailedEvent(any(Auction.class), any(Product.class));
            verify(auctionOutboxService, never()).saveAuctionWonEvent(any(Auction.class), any(Product.class));
        }

        @Test
        @DisplayName("성공 - PROGRESS 상태가 아니면 마감 확인만 생략한다")
        void success_skip_not_progress() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when
            auctionSchedulerJobService.checkAuctionEnd(auctionId);

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.READY);
            verify(auctionOutboxService, never()).saveAuctionFailedEvent(any(Auction.class), any(Product.class));
            verify(auctionOutboxService, never()).saveAuctionWonEvent(any(Auction.class), any(Product.class));
        }
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
