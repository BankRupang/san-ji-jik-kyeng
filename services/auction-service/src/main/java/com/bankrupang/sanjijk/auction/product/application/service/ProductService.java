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
import com.bankrupang.sanjijk.auction.product.presentation.dto.request.ProductUpdateRequest;
import com.bankrupang.sanjijk.auction.product.presentation.dto.response.ProductCreateResponse;
import com.bankrupang.sanjijk.auction.product.presentation.dto.response.ProductResponse;
import com.bankrupang.sanjijk.auction.product.presentation.dto.response.ProductUpdateResponse;
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

    public ProductResponse getProduct(UUID productId) {
        Product product = getExistingProduct(productId);

        return ProductResponse.from(product);
    }

    public PageResponse<ProductResponse> getProducts(int page, int size) {
        Pageable pageable = PageableUtils.ofDefault(page, size);

        Page<ProductResponse> products = productRepository
                .findAllByDeletedAtIsNull(pageable)
                .map(ProductResponse::from);

        return PageResponse.of(products);
    }

    @Transactional
    public ProductUpdateResponse updateProduct(UUID sellerId, String userRole, UUID productId, ProductUpdateRequest request) {
        validateSellerRole(userRole);
        validateUpdateRequest(request);

        Product product = getExistingProduct(productId);
        validateProductOwner(product, sellerId);

        product.update(
                request.name(),
                request.description(),
                request.quantity()
        );

        return ProductUpdateResponse.from(product);
    }

    private void validateSellerRole(String userRole) {
        if (!"SELLER".equalsIgnoreCase(userRole)) {
            throw new ProductException(ProductErrorCode.PRODUCT_FORBIDDEN);
        }
    }

    private Product getExistingProduct(UUID productId) {
        return productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    private void validateUpdateRequest(ProductUpdateRequest request) {
        if (request.name() == null && request.description() == null && request.quantity() == null) {
            throw new ProductException(ProductErrorCode.INVALID_PRODUCT_REQUEST);
        }

        validateNotBlankIfPresent(request.name());
        validateNotBlankIfPresent(request.description());
        validateNotBlankIfPresent(request.quantity());
    }

    private void validateNotBlankIfPresent(String value) {
        if (value != null && value.isBlank()) {
            throw new ProductException(ProductErrorCode.INVALID_PRODUCT_REQUEST);
        }
    }

    private void validateProductOwner(Product product, UUID sellerId) {
        if (!product.getSellerId().equals(sellerId)) {
            throw new ProductException(ProductErrorCode.PRODUCT_FORBIDDEN);
        }
    }

}
