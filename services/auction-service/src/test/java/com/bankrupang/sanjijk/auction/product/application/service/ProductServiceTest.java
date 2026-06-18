package com.bankrupang.sanjijk.auction.product.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.bankrupang.sanjijk.auction.product.domain.entity.Product;
import com.bankrupang.sanjijk.auction.product.domain.repository.ProductRepository;
import com.bankrupang.sanjijk.auction.product.exception.ProductErrorCode;
import com.bankrupang.sanjijk.auction.product.exception.ProductException;
import com.bankrupang.sanjijk.auction.product.presentation.dto.request.ProductCreateRequest;
import com.bankrupang.sanjijk.auction.product.presentation.dto.response.ProductCreateResponse;

@DisplayName("ProductService 테스트")
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Nested
    @DisplayName("createProduct()")
    class CreateProduct {

        @Test
        @DisplayName("성공 - 판매자가 상품을 등록한다")
        void success() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            LocalDateTime createdAt = LocalDateTime.now();
            ProductCreateRequest request = new ProductCreateRequest(
                    "사과",
                    "신선한 사과입니다.",
                    "10"
            );

            Product savedProduct = Product.create(
                    sellerId,
                    request.name(),
                    request.description(),
                    request.quantity()
            );
            ReflectionTestUtils.setField(savedProduct, "id", productId);
            ReflectionTestUtils.setField(savedProduct, "createdAt", createdAt);

            given(productRepository.save(any(Product.class))).willReturn(savedProduct);

            // when
            ProductCreateResponse result = productService.createProduct(sellerId, "SELLER", request);

            // then
            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.sellerId()).isEqualTo(sellerId);
            assertThat(result.name()).isEqualTo(request.name());
            assertThat(result.description()).isEqualTo(request.description());
            assertThat(result.quantity()).isEqualTo(request.quantity());
            assertThat(result.createdAt()).isEqualTo(createdAt);
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("실패 - 판매자가 아니면 상품을 등록할 수 없다")
        void fail_not_seller() {
            // given
            UUID sellerId = UUID.randomUUID();
            ProductCreateRequest request = new ProductCreateRequest(
                    "사과",
                    "신선한 사과입니다.",
                    "10"
            );

            // when & then
            assertThatThrownBy(() -> productService.createProduct(sellerId, "BUYER", request))
                    .isInstanceOf(ProductException.class)
                    .hasMessage(ProductErrorCode.PRODUCT_FORBIDDEN.getMessage());
            verify(productRepository, never()).save(any(Product.class));
        }
    }
}
