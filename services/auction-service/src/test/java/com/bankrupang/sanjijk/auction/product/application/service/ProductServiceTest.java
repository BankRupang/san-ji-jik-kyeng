package com.bankrupang.sanjijk.auction.product.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.bankrupang.sanjijk.auction.product.domain.entity.Product;
import com.bankrupang.sanjijk.auction.product.domain.repository.ProductRepository;
import com.bankrupang.sanjijk.auction.product.exception.ProductErrorCode;
import com.bankrupang.sanjijk.auction.product.exception.ProductException;
import com.bankrupang.sanjijk.auction.product.presentation.dto.request.ProductCreateRequest;
import com.bankrupang.sanjijk.auction.product.presentation.dto.request.ProductUpdateRequest;
import com.bankrupang.sanjijk.auction.product.presentation.dto.response.ProductCreateResponse;
import com.bankrupang.sanjijk.auction.product.presentation.dto.response.ProductResponse;
import com.bankrupang.sanjijk.auction.product.presentation.dto.response.ProductUpdateResponse;
import com.bankrupang.sanjijk.common.response.PageResponse;

@DisplayName("ProductService 테스트")
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

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
            ProductCreateResponse result = productService.createProduct(sellerId, request);

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
        @DisplayName("성공 - 마스터가 상품을 등록한다")
        void success_master() {
            // given
            UUID masterId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            LocalDateTime createdAt = LocalDateTime.now();
            ProductCreateRequest request = new ProductCreateRequest(
                    "사과",
                    "신선한 사과입니다.",
                    "10"
            );

            Product savedProduct = Product.create(
                    masterId,
                    request.name(),
                    request.description(),
                    request.quantity()
            );
            ReflectionTestUtils.setField(savedProduct, "id", productId);
            ReflectionTestUtils.setField(savedProduct, "createdAt", createdAt);

            given(productRepository.save(any(Product.class))).willReturn(savedProduct);

            // when
            ProductCreateResponse result = productService.createProduct(masterId, request);

            // then
            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.sellerId()).isEqualTo(masterId);
            verify(productRepository).save(any(Product.class));
        }

    }

    @Nested
    @DisplayName("getProduct()")
    class GetProduct {

        @Test
        @DisplayName("성공 - 상품을 단건 조회한다")
        void success() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            LocalDateTime createdAt = LocalDateTime.now();
            Product product = createProduct(sellerId, productId, createdAt);

            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));

            // when
            ProductResponse result = productService.getProduct(productId);

            // then
            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.sellerId()).isEqualTo(sellerId);
            assertThat(result.name()).isEqualTo(product.getName());
            assertThat(result.description()).isEqualTo(product.getDescription());
            assertThat(result.quantity()).isEqualTo(product.getQuantity());
            assertThat(result.createdAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("실패 - 상품이 존재하지 않는다")
        void fail_product_not_found() {
            // given
            UUID productId = UUID.randomUUID();
            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productService.getProduct(productId))
                    .isInstanceOf(ProductException.class)
                    .hasMessage(ProductErrorCode.PRODUCT_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("getProducts()")
    class GetProducts {

        @Test
        @DisplayName("성공 - 상품 목록을 최신 등록순으로 조회한다")
        void success() {
            // given
            Product firstProduct = createProduct(UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.now());
            Product secondProduct = createProduct(UUID.randomUUID(), UUID.randomUUID(), LocalDateTime.now().minusDays(1));
            given(productRepository.findAllByDeletedAtIsNull(any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(firstProduct, secondProduct)));

            // when
            PageResponse<ProductResponse> result = productService.getProducts(0, 10);

            // then
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).productId()).isEqualTo(firstProduct.getId());
            assertThat(result.getContent().get(1).productId()).isEqualTo(secondProduct.getId());
            assertThat(result.getPage()).isZero();
            assertThat(result.getSize()).isEqualTo(2);
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getTotalPages()).isEqualTo(1);
            assertThat(result.getSort()).isEqualTo("createdAt,DESC");

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(productRepository).findAllByDeletedAtIsNull(pageableCaptor.capture());
            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isZero();
            assertThat(pageable.getPageSize()).isEqualTo(10);
            assertThat(pageable.getSort().getOrderFor("createdAt").isDescending()).isTrue();
        }
    }

    @Nested
    @DisplayName("updateProduct()")
    class UpdateProduct {

        @Test
        @DisplayName("성공 - 판매자가 본인 상품을 부분 수정한다")
        void success_seller() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            Product product = createProduct(sellerId, productId, LocalDateTime.now());
            ProductUpdateRequest request = new ProductUpdateRequest(
                    "수정된 사과",
                    null,
                    "20"
            );

            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));

            // when
            ProductUpdateResponse result = productService.updateProduct(sellerId, productId, request);

            // then
            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.sellerId()).isEqualTo(sellerId);
            assertThat(result.name()).isEqualTo("수정된 사과");
            assertThat(result.description()).isEqualTo("신선한 사과입니다.");
            assertThat(result.quantity()).isEqualTo("20");
        }

        @Test
        @DisplayName("성공 - 마스터가 다른 판매자의 상품을 수정한다")
        void success_master() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID masterId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            Product product = createProduct(sellerId, productId, LocalDateTime.now());
            ProductUpdateRequest request = new ProductUpdateRequest(
                    "수정된 사과",
                    null,
                    null
            );

            setAuthentication("ROLE_MASTER");
            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));

            // when
            ProductUpdateResponse result = productService.updateProduct(masterId, productId, request);

            // then
            assertThat(result.productId()).isEqualTo(productId);
            assertThat(result.sellerId()).isEqualTo(sellerId);
            assertThat(result.name()).isEqualTo("수정된 사과");
            assertThat(result.description()).isEqualTo("신선한 사과입니다.");
            assertThat(result.quantity()).isEqualTo("10");
        }

        @Test
        @DisplayName("실패 - 상품이 존재하지 않으면 수정할 수 없다")
        void fail_product_not_found() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            ProductUpdateRequest request = new ProductUpdateRequest(
                    "수정된 사과",
                    null,
                    null
            );

            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productService.updateProduct(sellerId, productId, request))
                    .isInstanceOf(ProductException.class)
                    .hasMessage(ProductErrorCode.PRODUCT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("실패 - 판매자가 다른 판매자의 상품을 수정할 수 없다")
        void fail_not_product_owner() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID otherSellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            Product product = createProduct(sellerId, productId, LocalDateTime.now());
            ProductUpdateRequest request = new ProductUpdateRequest(
                    "수정된 사과",
                    null,
                    null
            );

            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));

            // when & then
            assertThatThrownBy(() -> productService.updateProduct(otherSellerId, productId, request))
                    .isInstanceOf(ProductException.class)
                    .hasMessage(ProductErrorCode.PRODUCT_FORBIDDEN.getMessage());
            assertThat(product.getName()).isEqualTo("사과");
        }

        @Test
        @DisplayName("실패 - 수정할 값이 하나도 없으면 수정할 수 없다")
        void fail_empty_update_request() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            ProductUpdateRequest request = new ProductUpdateRequest(
                    null,
                    null,
                    null
            );

            // when & then
            assertThatThrownBy(() -> productService.updateProduct(sellerId, productId, request))
                    .isInstanceOf(ProductException.class)
                    .hasMessage(ProductErrorCode.INVALID_PRODUCT_REQUEST.getMessage());
            verify(productRepository, never()).findByIdAndDeletedAtIsNull(any(UUID.class));
        }

        @Test
        @DisplayName("실패 - 수정 값이 공백이면 수정할 수 없다")
        void fail_blank_update_request() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            ProductUpdateRequest request = new ProductUpdateRequest(
                    " ",
                    null,
                    null
            );

            // when & then
            assertThatThrownBy(() -> productService.updateProduct(sellerId, productId, request))
                    .isInstanceOf(ProductException.class)
                    .hasMessage(ProductErrorCode.INVALID_PRODUCT_REQUEST.getMessage());
            verify(productRepository, never()).findByIdAndDeletedAtIsNull(any(UUID.class));
        }
    }

    @Nested
    @DisplayName("deleteProduct()")
    class DeleteProduct {

        @Test
        @DisplayName("성공 - 판매자가 본인 상품을 삭제한다")
        void success_seller() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            Product product = createProduct(sellerId, productId, LocalDateTime.now());

            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));

            // when
            productService.deleteProduct(sellerId, productId);

            // then
            assertThat(product.isDeleted()).isTrue();
            assertThat(product.getDeletedBy()).isEqualTo(sellerId);
        }

        @Test
        @DisplayName("성공 - 마스터가 다른 판매자의 상품을 삭제한다")
        void success_master() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID masterId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            Product product = createProduct(sellerId, productId, LocalDateTime.now());

            setAuthentication("ROLE_MASTER");
            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));

            // when
            productService.deleteProduct(masterId, productId);

            // then
            assertThat(product.isDeleted()).isTrue();
            assertThat(product.getDeletedBy()).isEqualTo(masterId);
        }

        @Test
        @DisplayName("실패 - 상품이 존재하지 않으면 삭제할 수 없다")
        void fail_product_not_found() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();

            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> productService.deleteProduct(sellerId, productId))
                    .isInstanceOf(ProductException.class)
                    .hasMessage(ProductErrorCode.PRODUCT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("실패 - 판매자가 다른 판매자의 상품을 삭제할 수 없다")
        void fail_not_product_owner() {
            // given
            UUID sellerId = UUID.randomUUID();
            UUID otherSellerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            Product product = createProduct(sellerId, productId, LocalDateTime.now());

            given(productRepository.findByIdAndDeletedAtIsNull(productId)).willReturn(Optional.of(product));

            // when & then
            assertThatThrownBy(() -> productService.deleteProduct(otherSellerId, productId))
                    .isInstanceOf(ProductException.class)
                    .hasMessage(ProductErrorCode.PRODUCT_FORBIDDEN.getMessage());
            assertThat(product.isDeleted()).isFalse();
        }
    }

    private Product createProduct(UUID sellerId, UUID productId, LocalDateTime createdAt) {
        Product product = Product.create(
                sellerId,
                "사과",
                "신선한 사과입니다.",
                "10"
        );
        ReflectionTestUtils.setField(product, "id", productId);
        ReflectionTestUtils.setField(product, "createdAt", createdAt);
        return product;
    }

    private void setAuthentication(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UUID.randomUUID().toString(),
                        null,
                        List.of(new SimpleGrantedAuthority(role))
                )
        );
    }
}
