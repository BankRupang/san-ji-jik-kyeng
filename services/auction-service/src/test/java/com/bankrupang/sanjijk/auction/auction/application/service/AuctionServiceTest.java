package com.bankrupang.sanjijk.auction.auction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.repository.AuctionRepository;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.auction.auction.exception.AuctionErrorCode;
import com.bankrupang.sanjijk.auction.auction.exception.AuctionException;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.request.AuctionCreateRequest;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionCreateResponse;
import com.bankrupang.sanjijk.auction.product.domain.entity.Product;
import com.bankrupang.sanjijk.auction.product.domain.repository.ProductRepository;
import com.bankrupang.sanjijk.auction.product.exception.ProductErrorCode;
import com.bankrupang.sanjijk.auction.product.exception.ProductException;

@DisplayName("AuctionService 테스트")
@ExtendWith(MockitoExtension.class)
class AuctionServiceTest {

    @InjectMocks
    private AuctionService auctionService;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private ProductRepository productRepository;

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

            // when
            AuctionCreateResponse result = auctionService.createAuction(masterId, "MASTER", request);

            // then
            assertThat(result.auctionId()).isEqualTo(auctionId);
            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.sellerId()).isEqualTo(sellerId);
            assertThat(result.endAt()).isEqualTo(startAt.plusHours(1));
            verify(auctionRepository).save(any(Auction.class));
        }

        @Test
        @DisplayName("실패 - 판매자 또는 마스터가 아니면 경매를 생성할 수 없다")
        void fail_forbidden_role() {
            // given
            UUID userId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            AuctionCreateRequest request = createRequest(productId, LocalDateTime.now().plusDays(1));

            // when & then
            assertThatThrownBy(() -> auctionService.createAuction(userId, "BUYER", request))
                    .isInstanceOf(AuctionException.class)
                    .hasMessage(AuctionErrorCode.AUCTION_FORBIDDEN.getMessage());
            verify(productRepository, never()).findByIdAndDeletedAtIsNull(any(UUID.class));
            verify(auctionRepository, never()).save(any(Auction.class));
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
}
