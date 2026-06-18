package com.bankrupang.sanjijk.auction.product.application.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.bankrupang.sanjijk.auction.product.domain.entity.Product;
import com.bankrupang.sanjijk.auction.product.domain.repository.ProductRepository;
import com.bankrupang.sanjijk.auction.product.exception.ProductErrorCode;
import com.bankrupang.sanjijk.auction.product.exception.ProductException;
import com.bankrupang.sanjijk.auction.product.presentation.dto.request.ProductCreateRequest;
import com.bankrupang.sanjijk.auction.product.presentation.dto.response.ProductCreateResponse;
import com.bankrupang.sanjijk.auction.product.presentation.dto.response.ProductDetailResponse;
import com.bankrupang.sanjijk.auction.product.presentation.dto.response.ProductListResponse;
import com.bankrupang.sanjijk.common.response.PageResponse;
import com.bankrupang.sanjijk.common.util.PageableUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional
    public ProductCreateResponse createProduct(UUID sellerId, String userRole, ProductCreateRequest request) {
        validateSellerRole(userRole);

        Product product = Product.create(
                sellerId,
                request.name(),
                request.description(),
                request.quantity()
        );

        Product savedProduct = productRepository.save(product);

        return ProductCreateResponse.from(savedProduct);
    }

    private void validateSellerRole(String userRole) {
        if (!"SELLER".equalsIgnoreCase(userRole)) {
            throw new ProductException(ProductErrorCode.PRODUCT_FORBIDDEN);
        }
    }

    public ProductDetailResponse getProduct(UUID productId) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));

        return ProductDetailResponse.from(product);
    }

    public PageResponse<ProductListResponse> getProducts(int page, int size) {
        Pageable pageable = PageableUtils.ofDefault(page, size);

        Page<ProductListResponse> products = productRepository
                .findAllByDeletedAtIsNull(pageable)
                .map(ProductListResponse::from);

        return PageResponse.of(products);
    }

}
