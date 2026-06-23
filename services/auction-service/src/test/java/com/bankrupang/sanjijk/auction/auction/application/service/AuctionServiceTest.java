package com.bankrupang.sanjijk.auction.auction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.repository.AuctionRepository;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.auction.auction.exception.AuctionErrorCode;
import com.bankrupang.sanjijk.auction.auction.exception.AuctionException;
import com.bankrupang.sanjijk.auction.auction.infrastructure.scheduler.AuctionScheduleManager;
import com.bankrupang.sanjijk.auction.auction.infrastructure.scheduler.AuctionSchedulerJobService;
import com.bankrupang.sanjijk.auction.auction.infrastructure.transaction.TransactionAfterCommitExecutor;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.request.AuctionCancelRequest;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.request.AuctionCloseRequest;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.request.AuctionCreateRequest;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.request.AuctionUpdateRequest;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionCancelResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionCloseResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionCreateResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionDetailResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionListResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionStartResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionUpdateResponse;
import com.bankrupang.sanjijk.auction.auction.infrastructure.client.BidClient;
import com.bankrupang.sanjijk.auction.auction.infrastructure.client.dto.HighestBidResponse;
import com.bankrupang.sanjijk.auction.outbox.application.service.AuctionOutboxService;
import com.bankrupang.sanjijk.auction.product.domain.entity.Product;
import com.bankrupang.sanjijk.auction.product.domain.repository.ProductRepository;
import com.bankrupang.sanjijk.auction.product.exception.ProductErrorCode;
import com.bankrupang.sanjijk.auction.product.exception.ProductException;
import com.bankrupang.sanjijk.common.response.PageResponse;

@DisplayName("AuctionService 테스트")
@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @InjectMocks
    private AuctionService auctionService;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AuctionOutboxService auctionOutboxService;

    @Mock
    private AuctionScheduleManager auctionScheduleManager;

    @Mock
    private AuctionSchedulerJobService auctionSchedulerJobService;

    @Mock
    private TransactionAfterCommitExecutor transactionAfterCommitExecutor;

    @Mock
    private BidClient bidClient;

    @Nested
    @DisplayName("createAuction()")
    class CreateAuction {

        @Test
        @DisplayName("성공 - 판매자가 본인 상품으로 경매를 생성한다")
        void success_seller() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            LocalDateTime createdAt = LocalDateTime.now();
            LocalDateTime startAt = LocalDateTime.now().plusDays(1);
            AuctionCreateRequest request = createRequest(productId, startAt);
            Product product = createProduct(sellerId, productId);

            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));
            given(auctionRepository.save(any(Auction.class))).willAnswer(invocation -> {
                Auction auction = invocation.getArgument(0);
                ReflectionTestUtils.setField(auction, "id", auctionId);
                ReflectionTestUtils.setField(auction, "createdAt", createdAt);
                return auction;
            });
            doAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                task.run();
                return null;
            }).when(transactionAfterCommitExecutor).execute(any(Runnable.class));

            // when
            AuctionCreateResponse result = auctionService.createAuction(sellerId, "SELLER", request);

            // then
            assertThat(result.auctionId()).isEqualTo(auctionId);
            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.sellerId()).isEqualTo(sellerId);
            assertThat(result.status()).isEqualTo(AuctionStatus.READY);
            assertThat(result.startPrice()).isEqualTo(request.startPrice());
            assertThat(result.bidUnit()).isEqualTo(request.bidUnit());
            assertThat(result.startAt()).isEqualTo(startAt);
            assertThat(result.endAt()).isEqualTo(startAt.plusHours(1));
            assertThat(result.createdAt()).isEqualTo(createdAt);

            ArgumentCaptor<Auction> auctionCaptor = ArgumentCaptor.forClass(Auction.class);
            verify(auctionRepository).save(auctionCaptor.capture());
            Auction savedAuction = auctionCaptor.getValue();
            assertThat(savedAuction.getProductId()).isEqualTo(productId);
            assertThat(savedAuction.getSellerId()).isEqualTo(sellerId);
            assertThat(savedAuction.getStatus()).isEqualTo(AuctionStatus.READY);
            assertThat(savedAuction.getEndAt()).isEqualTo(startAt.plusHours(1));
            verify(transactionAfterCommitExecutor).execute(any(Runnable.class));
            verify(auctionScheduleManager).scheduleStartJob(any(UUID.class), any(LocalDateTime.class), any(Runnable.class));
        }

        @Test
        @DisplayName("성공 - 마스터가 다른 판매자의 상품으로 경매를 생성한다")
        void success_master() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID masterId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            LocalDateTime createdAt = LocalDateTime.now();
            LocalDateTime startAt = LocalDateTime.now().plusDays(1);
            AuctionCreateRequest request = createRequest(productId, startAt);
            Product product = createProduct(sellerId, productId);

            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));
            given(auctionRepository.save(any(Auction.class))).willAnswer(invocation -> {
                Auction auction = invocation.getArgument(0);
                ReflectionTestUtils.setField(auction, "id", auctionId);
                ReflectionTestUtils.setField(auction, "createdAt", createdAt);
                return auction;
            });
            doAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                task.run();
                return null;
            }).when(transactionAfterCommitExecutor).execute(any(Runnable.class));

            // when
            AuctionCreateResponse result = auctionService.createAuction(masterId, "MASTER", request);

            // then
            assertThat(result.auctionId()).isEqualTo(auctionId);
            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.sellerId()).isEqualTo(sellerId);
            assertThat(result.endAt()).isEqualTo(startAt.plusHours(1));
            verify(auctionRepository).save(any(Auction.class));
            verify(auctionScheduleManager).scheduleStartJob(any(UUID.class), any(LocalDateTime.class), any(Runnable.class));
        }

        @Test
        @DisplayName("실패 - 상품이 존재하지 않으면 경매를 생성할 수 없다")
        void fail_product_not_found() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            AuctionCreateRequest request = createRequest(productId, LocalDateTime.now().plusDays(1));

            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> auctionService.createAuction(sellerId, "SELLER", request))
                    .isInstanceOf(ProductException.class)
                    .hasMessage(ProductErrorCode.PRODUCT_NOT_FOUND.getMessage());
            verify(auctionRepository, never()).save(any(Auction.class));
        }

        @Test
        @DisplayName("실패 - 판매자가 다른 판매자의 상품으로 경매를 생성할 수 없다")
        void fail_not_product_owner() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID otherSellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            AuctionCreateRequest request = createRequest(productId, LocalDateTime.now().plusDays(1));
            Product product = createProduct(sellerId, productId);

            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));

            // when & then
            assertThatThrownBy(() -> auctionService.createAuction(otherSellerId, "SELLER", request))
                    .isInstanceOf(AuctionException.class)
                    .hasMessage(AuctionErrorCode.AUCTION_FORBIDDEN.getMessage());
            verify(auctionRepository, never()).save(any(Auction.class));
        }

        @Test
        @DisplayName("실패 - 경매 시작 시각이 현재 시각 이후가 아니면 생성할 수 없다")
        void fail_invalid_start_at() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            AuctionCreateRequest request = createRequest(productId, LocalDateTime.now().minusMinutes(1));
            Product product = createProduct(sellerId, productId);

            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));

            // when & then
            assertThatThrownBy(() -> auctionService.createAuction(sellerId, "SELLER", request))
                    .isInstanceOf(AuctionException.class)
                    .hasMessage(AuctionErrorCode.INVALID_AUCTION_PERIOD.getMessage());
            verify(auctionRepository, never()).save(any(Auction.class));
        }

        @Test
        @DisplayName("실패 - 이미 진행 중이거나 대기 중인 경매가 존재하면 경매를 생성할 수 없다")
        void fail_duplicate_auction() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            AuctionCreateRequest request = createRequest(productId, LocalDateTime.now().plusDays(1));
            Product product = createProduct(sellerId, productId);

            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));
            given(auctionRepository.existsByProductIdAndStatusInAndDeletedAtIsNull(any(UUID.class), any(Collection.class)))
                    .willReturn(true);

            // when & then
            assertThatThrownBy(() -> auctionService.createAuction(sellerId, "SELLER", request))
                    .isInstanceOf(AuctionException.class)
                    .hasMessage(AuctionErrorCode.DUPLICATE_AUCTION.getMessage());
            verify(auctionRepository, never()).save(any(Auction.class));
        }
    }

    @Nested
    @DisplayName("getAuction()")
    class GetAuction {

        @Test
        @DisplayName("성공 - 경매를 단건 조회한다")
        void success() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Product product = createProduct(sellerId, productId);
            Auction auction = createAuction(sellerId, productId, auctionId);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));
            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));

            // when
            AuctionDetailResponse result = auctionService.getAuction(auctionId);

            // then
            assertThat(result.auctionId()).isEqualTo(auctionId);
            assertThat(result.product().productId()).isEqualTo(productId);
            assertThat(result.product().name()).isEqualTo(product.getName());
            assertThat(result.product().description()).isEqualTo(product.getDescription());
            assertThat(result.product().quantity()).isEqualTo(product.getQuantity());
            assertThat(result.sellerId()).isEqualTo(sellerId);
            assertThat(result.status()).isEqualTo(AuctionStatus.READY);
            assertThat(result.bidUnit()).isEqualTo(1000);
            assertThat(result.startPrice()).isEqualTo(10000);
            assertThat(result.startAt()).isEqualTo(auction.getStartAt());
            assertThat(result.endAt()).isEqualTo(auction.getEndAt());
        }

        @Test
        @DisplayName("성공 - PROGRESS 상태의 경매를 조회할 때 bid-service를 통해 현재 최고가 정보를 조회해와 반영한다")
        void success_progress_with_highest_bid() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            UUID winnerId = UUID.randomUUID();
            int finalPrice = 15000;
            Product product = createProduct(sellerId, productId);
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start(); // READY -> PROGRESS

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));
            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));
            given(bidClient.getHighestBid(auctionId)).willReturn(new HighestBidResponse(winnerId, finalPrice));

            // when
            AuctionDetailResponse result = auctionService.getAuction(auctionId);

            // then
            assertThat(result.auctionId()).isEqualTo(auctionId);
            assertThat(result.status()).isEqualTo(AuctionStatus.PROGRESS);
            assertThat(result.winnerId()).isEqualTo(winnerId);
            assertThat(result.finalPrice()).isEqualTo(finalPrice);
        }

        @Test
        @DisplayName("성공 - PROGRESS 상태의 경매 조회 시 bid-service 조회 실패(예외)하더라도 기존 경매 정보를 그대로 반환한다")
        void success_progress_highest_bid_fail_graceful() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Product product = createProduct(sellerId, productId);
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start(); // READY -> PROGRESS

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));
            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));
            given(bidClient.getHighestBid(auctionId)).willThrow(new RuntimeException("Feign Exception"));

            // when
            AuctionDetailResponse result = auctionService.getAuction(auctionId);

            // then
            assertThat(result.auctionId()).isEqualTo(auctionId);
            assertThat(result.status()).isEqualTo(AuctionStatus.PROGRESS);
            assertThat(result.winnerId()).isNull();
            assertThat(result.finalPrice()).isNull();
        }

        @Test
        @DisplayName("실패 - 경매가 존재하지 않는다")
        void fail_auction_not_found() {
            // given
            UUID auctionId = UUID.randomUUID();
            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> auctionService.getAuction(auctionId))
                    .isInstanceOf(AuctionException.class)
                    .hasMessage(AuctionErrorCode.AUCTION_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("실패 - 경매 상품이 존재하지 않는다")
        void fail_product_not_found() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));
            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> auctionService.getAuction(auctionId))
                    .isInstanceOf(ProductException.class)
                    .hasMessage(ProductErrorCode.PRODUCT_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("getAuctions()")
    class GetAuctions {

        @Test
        @DisplayName("성공 - 경매 목록을 최신 등록순으로 조회한다")
        void success() {
            // given
            UUID firstSellerId = UUID.randomUUID();
            UUID firstProductId = UUID.randomUUID();
            UUID firstAuctionId = UUID.randomUUID();
            UUID secondSellerId = UUID.randomUUID();
            UUID secondProductId = UUID.randomUUID();
            UUID secondAuctionId = UUID.randomUUID();
            Auction firstAuction = createAuction(firstSellerId, firstProductId, firstAuctionId);
            Auction secondAuction = createAuction(secondSellerId, secondProductId, secondAuctionId);
            Product firstProduct = createProduct(firstSellerId, firstProductId);
            Product secondProduct = createProduct(secondSellerId, secondProductId);

            given(auctionRepository.findAllByDeletedAtIsNull(any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(firstAuction, secondAuction)));
            given(productRepository.findAllByIdInAndDeletedAtIsNull(any()))
                    .willReturn(List.of(firstProduct, secondProduct));

            // when
            PageResponse<AuctionListResponse> result = auctionService.getAuctions(0, 10, null);

            // then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).auctionId()).isEqualTo(firstAuctionId);
            assertThat(result.getContent().get(0).productName()).isEqualTo(firstProduct.getName());
            assertThat(result.getContent().get(1).auctionId()).isEqualTo(secondAuctionId);
            assertThat(result.getContent().get(1).productName()).isEqualTo(secondProduct.getName());
            assertThat(result.getPage()).isZero();
            assertThat(result.getSize()).isEqualTo(10);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getTotalPages()).isEqualTo(1);
            assertThat(result.getSort()).isEqualTo("createdAt,DESC");

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(auctionRepository).findAllByDeletedAtIsNull(pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(10);
            assertThat(pageable.getSort().getOrderFor("createdAt").isDescending()).isTrue();
            verify(auctionRepository, never()).findAllByStatusAndDeletedAtIsNull(any(AuctionStatus.class), any(Pageable.class));
            verify(productRepository).findAllByIdInAndDeletedAtIsNull(any());
            verify(productRepository, never()).findByIdAndDeletedAtIsNull(any(UUID.class));
        }

        @Test
        @DisplayName("성공 - 상태별 경매 목록을 조회한다")
        void success_filter_status() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            Product product = createProduct(sellerId, productId);

            given(auctionRepository.findAllByStatusAndDeletedAtIsNull(any(AuctionStatus.class), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(auction)));
            given(productRepository.findAllByIdInAndDeletedAtIsNull(any()))
                    .willReturn(List.of(product));

            // when
            PageResponse<AuctionListResponse> result = auctionService.getAuctions(0, 10, AuctionStatus.READY);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).auctionId()).isEqualTo(auctionId);
            assertThat(result.getContent().get(0).status()).isEqualTo(AuctionStatus.READY);
            assertThat(result.getContent().get(0).productName()).isEqualTo(product.getName());
            verify(auctionRepository).findAllByStatusAndDeletedAtIsNull(any(AuctionStatus.class), any(Pageable.class));
            verify(auctionRepository, never()).findAllByDeletedAtIsNull(any(Pageable.class));
            verify(productRepository).findAllByIdInAndDeletedAtIsNull(any());
            verify(productRepository, never()).findByIdAndDeletedAtIsNull(any(UUID.class));
        }

        @Test
        @DisplayName("성공 - 경매 목록의 상품이 존재하지 않으면 해당 항목을 제외한다")
        void success_exclude_missing_product() {
            // given
            UUID firstSellerId = UUID.randomUUID();
            UUID firstProductId = UUID.randomUUID();
            UUID firstAuctionId = UUID.randomUUID();
            UUID secondSellerId = UUID.randomUUID();
            UUID secondProductId = UUID.randomUUID();
            UUID secondAuctionId = UUID.randomUUID();
            Auction firstAuction = createAuction(firstSellerId, firstProductId, firstAuctionId);
            Auction secondAuction = createAuction(secondSellerId, secondProductId, secondAuctionId);
            Product firstProduct = createProduct(firstSellerId, firstProductId);

            given(auctionRepository.findAllByDeletedAtIsNull(any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(firstAuction, secondAuction)));
            given(productRepository.findAllByIdInAndDeletedAtIsNull(any()))
                    .willReturn(List.of(firstProduct));

            // when
            PageResponse<AuctionListResponse> result = auctionService.getAuctions(0, 10, null);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).auctionId()).isEqualTo(firstAuctionId);
            assertThat(result.getContent().get(0).productName()).isEqualTo(firstProduct.getName());
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("startAuctionManually()")
    class StartAuctionManually {

        @Test
        @DisplayName("성공 - READY 상태 경매를 PROGRESS 상태로 변경한다")
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
            AuctionStartResponse result = auctionService.startAuctionManually(auctionId);

            // then
            assertThat(result.auctionId()).isEqualTo(auctionId);
            assertThat(result.status()).isEqualTo(AuctionStatus.PROGRESS);
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PROGRESS);
            verify(auctionOutboxService).saveAuctionStartEvent(auction, product);
        }

        @Test
        @DisplayName("실패 - READY 상태가 아니면 시작할 수 없다")
        void fail_invalid_state() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            UUID winnerId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();
            auction.markResultPending();
            auction.markWon(winnerId, 15000);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when & then
            assertThatThrownBy(() -> auctionService.startAuctionManually(auctionId))
                    .isInstanceOf(AuctionException.class)
                    .hasMessage(AuctionErrorCode.INVALID_STATE_TRANSITION.getMessage());
            verify(auctionOutboxService, never()).saveAuctionStartEvent(any(Auction.class), any(Product.class));
        }
    }

    @Nested
    @DisplayName("updateAuction()")
    class UpdateAuction {

        @Test
        @DisplayName("성공 - 시작 시각을 수정하면 시작 잡을 재등록한다")
        void success_reschedule_start_job() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            LocalDateTime newStartAt = LocalDateTime.now().plusDays(2);
            Auction auction = createAuction(sellerId, productId, auctionId);
            AuctionUpdateRequest request = new AuctionUpdateRequest(null, null, newStartAt);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));
            doAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                task.run();
                return null;
            }).when(transactionAfterCommitExecutor).execute(any(Runnable.class));

            // when
            AuctionUpdateResponse result = auctionService.updateAuction(sellerId, "SELLER", auctionId, request);

            // then
            assertThat(result.auctionId()).isEqualTo(auctionId);
            assertThat(result.startAt()).isEqualTo(newStartAt);
            assertThat(result.endAt()).isEqualTo(newStartAt.plusHours(1));
            verify(transactionAfterCommitExecutor).execute(any(Runnable.class));
            verify(auctionScheduleManager).scheduleStartJob(any(UUID.class), any(LocalDateTime.class), any(Runnable.class));
        }

        @Test
        @DisplayName("성공 - 시작 시각을 수정하지 않으면 시작 잡을 재등록하지 않는다")
        void success_not_reschedule_without_start_at() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            AuctionUpdateRequest request = new AuctionUpdateRequest(20000, 2000, null);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when
            AuctionUpdateResponse result = auctionService.updateAuction(sellerId, "SELLER", auctionId, request);

            // then
            assertThat(result.startPrice()).isEqualTo(20000);
            assertThat(result.bidUnit()).isEqualTo(2000);
            verify(transactionAfterCommitExecutor, never()).execute(any(Runnable.class));
            verify(auctionScheduleManager, never()).scheduleStartJob(any(UUID.class), any(LocalDateTime.class), any(Runnable.class));
        }
    }

    @Nested
    @DisplayName("cancelAuction()")
    class CancelAuction {

        @Test
        @DisplayName("성공 - 경매를 취소하면 시작 잡을 취소한다")
        void success_cancel_start_job() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            AuctionCancelRequest request = new AuctionCancelRequest("판매자 요청 취소");

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));
            doAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                task.run();
                return null;
            }).when(transactionAfterCommitExecutor).execute(any(Runnable.class));

            // when
            AuctionCancelResponse result = auctionService.cancelAuction(sellerId, "SELLER", auctionId, request);

            // then
            assertThat(result.auctionId()).isEqualTo(auctionId);
            assertThat(result.status()).isEqualTo(AuctionStatus.CANCELLED);
            assertThat(result.reason()).isEqualTo(request.reason());
            verify(transactionAfterCommitExecutor).execute(any(Runnable.class));
            verify(auctionScheduleManager).cancelStartJob(auctionId);
        }
    }

    @Nested
    @DisplayName("closeAuctionManually()")
    class CloseAuctionManually {

        @Test
        @DisplayName("성공 - 최고가와 낙찰자가 있으면 낙찰 처리한다")
        void success_won() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            UUID winnerId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            Product product = createProduct(sellerId, productId);
            auction.start();
            AuctionCloseRequest request = new AuctionCloseRequest(winnerId, 15000);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));
            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));

            // when
            AuctionCloseResponse result = auctionService.closeAuctionManually(auctionId, request);

            // then
            assertThat(result.auctionId()).isEqualTo(auctionId);
            assertThat(result.status()).isEqualTo(AuctionStatus.WON);
            assertThat(result.winnerId()).isEqualTo(winnerId);
            assertThat(result.finalPrice()).isEqualTo(15000);
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.WON);
            verify(auctionOutboxService).saveAuctionWonEvent(auction, product);
            verify(auctionOutboxService, never()).saveAuctionFailedEvent(any(Auction.class), any(Product.class));
        }

        @Test
        @DisplayName("성공 - 낙찰 정보가 없으면 유찰 처리한다")
        void success_fail() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            Product product = createProduct(sellerId, productId);
            auction.start();

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));
            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));

            // when
            AuctionCloseResponse result = auctionService.closeAuctionManually(auctionId, null);

            // then
            assertThat(result.auctionId()).isEqualTo(auctionId);
            assertThat(result.status()).isEqualTo(AuctionStatus.FAIL);
            assertThat(result.winnerId()).isNull();
            assertThat(result.finalPrice()).isNull();
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAIL);
            verify(auctionOutboxService).saveAuctionFailedEvent(auction, product);
            verify(auctionOutboxService, never()).saveAuctionWonEvent(any(Auction.class), any(Product.class));
        }

        @Test
        @DisplayName("성공 - 이미 낙찰 처리된 경매는 현재 상태를 그대로 반환한다")
        void success_idempotent_won() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            UUID winnerId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();
            auction.markResultPending();
            auction.markWon(winnerId, 15000);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when
            AuctionCloseResponse result = auctionService.closeAuctionManually(
                    auctionId,
                    new AuctionCloseRequest(winnerId, 15000)
            );

            // then
            assertThat(result.auctionId()).isEqualTo(auctionId);
            assertThat(result.status()).isEqualTo(AuctionStatus.WON);
            assertThat(result.winnerId()).isEqualTo(winnerId);
            assertThat(result.finalPrice()).isEqualTo(15000);
            verify(auctionOutboxService, never()).saveAuctionWonEvent(any(Auction.class), any(Product.class));
            verify(auctionOutboxService, never()).saveAuctionFailedEvent(any(Auction.class), any(Product.class));
        }

        @Test
        @DisplayName("성공 - 이미 유찰 처리된 경매는 현재 상태를 그대로 반환한다")
        void success_idempotent_fail() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();
            auction.markResultPending();
            auction.markFailed();

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when
            AuctionCloseResponse result = auctionService.closeAuctionManually(auctionId, null);

            // then
            assertThat(result.auctionId()).isEqualTo(auctionId);
            assertThat(result.status()).isEqualTo(AuctionStatus.FAIL);
            assertThat(result.winnerId()).isNull();
            assertThat(result.finalPrice()).isNull();
            verify(auctionOutboxService, never()).saveAuctionWonEvent(any(Auction.class), any(Product.class));
            verify(auctionOutboxService, never()).saveAuctionFailedEvent(any(Auction.class), any(Product.class));
        }

        @Test
        @DisplayName("실패 - PROGRESS 상태가 아니면 마감할 수 없다")
        void fail_invalid_state() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when & then
            assertThatThrownBy(() -> auctionService.closeAuctionManually(auctionId, null))
                    .isInstanceOf(AuctionException.class)
                    .hasMessage(AuctionErrorCode.INVALID_STATE_TRANSITION.getMessage());
            verify(auctionOutboxService, never()).saveAuctionWonEvent(any(Auction.class), any(Product.class));
            verify(auctionOutboxService, never()).saveAuctionFailedEvent(any(Auction.class), any(Product.class));
        }

        @Test
        @DisplayName("실패 - 낙찰자와 최종 낙찰가 중 하나만 있으면 마감할 수 없다")
        void fail_incomplete_winning_result() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            UUID winnerId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();
            AuctionCloseRequest request = new AuctionCloseRequest(winnerId, null);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when & then
            assertThatThrownBy(() -> auctionService.closeAuctionManually(auctionId, request))
                    .isInstanceOf(AuctionException.class)
                    .hasMessage(AuctionErrorCode.INVALID_AUCTION_RESULT.getMessage());
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PROGRESS);
            verify(auctionOutboxService, never()).saveAuctionWonEvent(any(Auction.class), any(Product.class));
            verify(auctionOutboxService, never()).saveAuctionFailedEvent(any(Auction.class), any(Product.class));
        }

        @Test
        @DisplayName("실패 - 최종 낙찰가가 시작가보다 낮으면 마감할 수 없다")
        void fail_final_price_less_than_start_price() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            UUID winnerId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();
            AuctionCloseRequest request = new AuctionCloseRequest(winnerId, 9000);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when & then
            assertThatThrownBy(() -> auctionService.closeAuctionManually(auctionId, request))
                    .isInstanceOf(AuctionException.class)
                    .hasMessage(AuctionErrorCode.INVALID_AUCTION_RESULT.getMessage());
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PROGRESS);
            verify(auctionOutboxService, never()).saveAuctionWonEvent(any(Auction.class), any(Product.class));
            verify(auctionOutboxService, never()).saveAuctionFailedEvent(any(Auction.class), any(Product.class));
        }
    }

    @Nested
    @DisplayName("closeAuctionByEndedEvent()")
    class CloseAuctionByEndedEvent {

        @Test
        @DisplayName("성공 - 입찰이 있으면 낙찰 처리하고 Outbox에 낙찰 이벤트를 저장한다")
        void success_won() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            UUID winnerId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            Product product = createProduct(sellerId, productId);
            auction.start();

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));
            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));
            executeAfterCommitImmediately();

            // when
            AuctionCloseResponse result = auctionService.closeAuctionByEndedEvent(
                    auctionId,
                    true,
                    winnerId,
                    15000
            );

            // then
            assertThat(result.auctionId()).isEqualTo(auctionId);
            assertThat(result.status()).isEqualTo(AuctionStatus.WON);
            assertThat(result.winnerId()).isEqualTo(winnerId);
            assertThat(result.finalPrice()).isEqualTo(15000);
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.WON);
            verify(auctionOutboxService).saveAuctionWonEvent(auction, product);
            verify(auctionOutboxService, never()).saveAuctionFailedEvent(any(Auction.class), any(Product.class));
            verify(auctionScheduleManager).cancelEndCheckJob(auctionId);
        }

        @Test
        @DisplayName("성공 - 입찰이 없으면 유찰 처리하고 Outbox에 유찰 이벤트를 저장한다")
        void success_fail() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            Product product = createProduct(sellerId, productId);
            auction.start();

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));
            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));
            executeAfterCommitImmediately();

            // when
            AuctionCloseResponse result = auctionService.closeAuctionByEndedEvent(
                    auctionId,
                    false,
                    null,
                    null
            );

            // then
            assertThat(result.auctionId()).isEqualTo(auctionId);
            assertThat(result.status()).isEqualTo(AuctionStatus.FAIL);
            assertThat(result.winnerId()).isNull();
            assertThat(result.finalPrice()).isNull();
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAIL);
            verify(auctionOutboxService).saveAuctionFailedEvent(auction, product);
            verify(auctionOutboxService, never()).saveAuctionWonEvent(any(Auction.class), any(Product.class));
            verify(auctionScheduleManager).cancelEndCheckJob(auctionId);
        }

        @Test
        @DisplayName("성공 - 이미 낙찰 처리된 경매는 멱등하게 현재 상태만 반환한다")
        void success_idempotent_won() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            UUID winnerId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();
            auction.markResultPending();
            auction.markWon(winnerId, 15000);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when
            AuctionCloseResponse result = auctionService.closeAuctionByEndedEvent(
                    auctionId,
                    true,
                    winnerId,
                    15000
            );

            // then
            assertThat(result.auctionId()).isEqualTo(auctionId);
            assertThat(result.status()).isEqualTo(AuctionStatus.WON);
            assertThat(result.winnerId()).isEqualTo(winnerId);
            assertThat(result.finalPrice()).isEqualTo(15000);
            verify(auctionOutboxService, never()).saveAuctionWonEvent(any(Auction.class), any(Product.class));
            verify(auctionOutboxService, never()).saveAuctionFailedEvent(any(Auction.class), any(Product.class));
            verify(transactionAfterCommitExecutor, never()).execute(any(Runnable.class));
        }

        @Test
        @DisplayName("실패 - 입찰이 있다고 왔지만 낙찰 정보가 불완전하면 예외가 발생한다")
        void fail_incomplete_winning_result() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            UUID winnerId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when & then
            assertThatThrownBy(() -> auctionService.closeAuctionByEndedEvent(
                    auctionId,
                    true,
                    winnerId,
                    null
            ))
                    .isInstanceOf(AuctionException.class)
                    .hasMessage(AuctionErrorCode.INVALID_AUCTION_RESULT.getMessage());
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PROGRESS);
            verify(auctionOutboxService, never()).saveAuctionWonEvent(any(Auction.class), any(Product.class));
            verify(auctionOutboxService, never()).saveAuctionFailedEvent(any(Auction.class), any(Product.class));
            verify(transactionAfterCommitExecutor, never()).execute(any(Runnable.class));
        }
    }

    @Nested
    @DisplayName("extendAuctionByExtendedEvent()")
    class ExtendAuctionByExtendedEvent {

        @Test
        @DisplayName("성공 - 진행 중인 경매의 종료 시각을 갱신하고 마감 확인 잡을 재등록한다")
        void success() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();
            LocalDateTime newEndAt = auction.getEndAt().plusMinutes(5);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));
            executeAfterCommitImmediately();

            // when
            auctionService.extendAuctionByExtendedEvent(auctionId, newEndAt);

            // then
            assertThat(auction.getEndAt()).isEqualTo(newEndAt);
            assertThat(auction.getExtensionCount()).isEqualTo(1);
            verify(transactionAfterCommitExecutor).execute(any(Runnable.class));
            verify(auctionScheduleManager).scheduleEndCheckJob(any(UUID.class), any(LocalDateTime.class), any(Runnable.class));
        }

        @Test
        @DisplayName("성공 - 이미 종료된 경매의 연장 이벤트는 무시한다")
        void success_ignore_already_closed() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            UUID winnerId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();
            auction.markResultPending();
            auction.markWon(winnerId, 15000);
            LocalDateTime originalEndAt = auction.getEndAt();
            LocalDateTime newEndAt = originalEndAt.plusMinutes(5);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when
            auctionService.extendAuctionByExtendedEvent(auctionId, newEndAt);

            // then
            assertThat(auction.getEndAt()).isEqualTo(originalEndAt);
            assertThat(auction.getExtensionCount()).isZero();
            verify(transactionAfterCommitExecutor, never()).execute(any(Runnable.class));
            verify(auctionScheduleManager, never()).scheduleEndCheckJob(any(UUID.class), any(LocalDateTime.class), any(Runnable.class));
        }

        @Test
        @DisplayName("성공 - 기존 종료 시각 이후가 아닌 연장 이벤트는 무시한다")
        void success_ignore_stale_event() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();
            LocalDateTime originalEndAt = auction.getEndAt();

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when
            auctionService.extendAuctionByExtendedEvent(auctionId, originalEndAt);

            // then
            assertThat(auction.getEndAt()).isEqualTo(originalEndAt);
            assertThat(auction.getExtensionCount()).isZero();
            verify(transactionAfterCommitExecutor, never()).execute(any(Runnable.class));
            verify(auctionScheduleManager, never()).scheduleEndCheckJob(any(UUID.class), any(LocalDateTime.class), any(Runnable.class));
        }

        @Test
        @DisplayName("실패 - 새 종료 시각이 없으면 예외가 발생한다")
        void fail_null_new_end_at() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when & then
            assertThatThrownBy(() -> auctionService.extendAuctionByExtendedEvent(auctionId, null))
                    .isInstanceOf(AuctionException.class)
                    .hasMessage(AuctionErrorCode.INVALID_AUCTION_PERIOD.getMessage());
            verify(transactionAfterCommitExecutor, never()).execute(any(Runnable.class));
            verify(auctionScheduleManager, never()).scheduleEndCheckJob(any(UUID.class), any(LocalDateTime.class), any(Runnable.class));
        }
    }

    @Nested
    @DisplayName("completeAuctionPayment()")
    class CompleteAuctionPayment {

        @Test
        @DisplayName("성공 - WON 상태 경매를 SUCCESS 상태로 변경한다")
        void success() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            UUID winnerId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();
            auction.markResultPending();
            auction.markWon(winnerId, 15000);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when
            auctionService.completeAuctionPayment(auctionId);

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.SUCCESS);
        }

        @Test
        @DisplayName("성공 - 이미 SUCCESS 상태이면 멱등하게 처리한다")
        void success_idempotent_success() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            ReflectionTestUtils.setField(auction, "status", AuctionStatus.SUCCESS);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when
            auctionService.completeAuctionPayment(auctionId);

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.SUCCESS);
        }

        @Test
        @DisplayName("성공 - 이미 FAIL 상태이면 멱등하게 처리한다")
        void success_idempotent_fail() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();
            auction.markResultPending();
            auction.markFailed();

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when
            auctionService.completeAuctionPayment(auctionId);

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAIL);
        }

        @Test
        @DisplayName("실패 - WON 상태가 아니면 결제 완료 처리할 수 없다")
        void fail_invalid_state() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when & then
            assertThatThrownBy(() -> auctionService.completeAuctionPayment(auctionId))
                    .isInstanceOf(AuctionException.class)
                    .hasMessage(AuctionErrorCode.INVALID_STATE_TRANSITION.getMessage());
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.READY);
        }
    }

    @Nested
    @DisplayName("failAuctionPayment()")
    class FailAuctionPayment {

        @Test
        @DisplayName("성공 - WON 상태 경매를 FAIL 상태로 변경한다")
        void success() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            UUID winnerId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();
            auction.markResultPending();
            auction.markWon(winnerId, 15000);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when
            auctionService.failAuctionPayment(auctionId);

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAIL);
        }

        @Test
        @DisplayName("성공 - 이미 FAIL 상태이면 멱등하게 처리한다")
        void success_idempotent_fail() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            auction.start();
            auction.markResultPending();
            auction.markFailed();

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when
            auctionService.failAuctionPayment(auctionId);

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAIL);
        }

        @Test
        @DisplayName("성공 - 이미 SUCCESS 상태이면 멱등하게 처리한다")
        void success_idempotent_success() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);
            ReflectionTestUtils.setField(auction, "status", AuctionStatus.SUCCESS);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when
            auctionService.failAuctionPayment(auctionId);

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.SUCCESS);
        }

        @Test
        @DisplayName("실패 - WON 상태가 아니면 결제 실패 처리할 수 없다")
        void fail_invalid_state() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            UUID auctionId = UUID.randomUUID();
            Auction auction = createAuction(sellerId, productId, auctionId);

            given(auctionRepository.findByIdAndDeletedAtIsNull(auctionId)).willReturn(Optional.of(auction));

            // when & then
            assertThatThrownBy(() -> auctionService.failAuctionPayment(auctionId))
                    .isInstanceOf(AuctionException.class)
                    .hasMessage(AuctionErrorCode.INVALID_STATE_TRANSITION.getMessage());
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.READY);
        }
    }

    private AuctionCreateRequest createRequest(UUID productId, LocalDateTime startAt) {
        return new AuctionCreateRequest(
                productId,
                10000,
                1000,
                startAt
        );
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

    private void executeAfterCommitImmediately() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(transactionAfterCommitExecutor).execute(any(Runnable.class));
    }

}
