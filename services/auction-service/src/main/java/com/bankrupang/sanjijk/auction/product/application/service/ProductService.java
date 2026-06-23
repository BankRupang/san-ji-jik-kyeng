package com.bankrupang.sanjijk.auction.product.application.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.repository.AuctionRepository;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
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

    private static final String PRODUCT_DELETE_CANCEL_REASON = "상품 삭제로 경매가 취소되었습니다.";

    private final ProductRepository productRepository;
    private final AuctionRepository auctionRepository;

    @Transactional
    public ProductCreateResponse createProduct(UUID userId, ProductCreateRequest request) {
        Product product = Product.create(
                userId,
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
    public ProductUpdateResponse updateProduct(UUID userId, String userRole, UUID productId, ProductUpdateRequest request) {
        validateUpdateRequest(request);

        Product product = getExistingProduct(productId);
        validateProductOwnerOrManagerOrMaster(product, userId, userRole);
        validateLinkedAuctionEditable(productId);

        product.update(
                request.name(),
                request.description(),
                request.quantity()
        );

        return ProductUpdateResponse.from(product);
    }

    @Transactional
    public void deleteProduct(UUID userId, String userRole, UUID productId) {
        Product product = getExistingProduct(productId);
        validateProductOwnerOrManagerOrMaster(product, userId, userRole);
        deleteLinkedReadyAuction(userId, productId);

        product.softDelete(userId);
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

    private void validateLinkedAuctionEditable(UUID productId) {
        auctionRepository.findByProductIdAndDeletedAtIsNull(productId)
                .ifPresent(this::validateReadyAuction);
    }

    private void deleteLinkedReadyAuction(UUID userId, UUID productId) {
        auctionRepository.findByProductIdAndDeletedAtIsNull(productId)
                .ifPresent(auction -> {
                    validateReadyAuction(auction);
                    auction.cancel(PRODUCT_DELETE_CANCEL_REASON);
                    auction.softDelete(userId);
                });
    }

    private void validateReadyAuction(Auction auction) {
        if (auction.getStatus() != AuctionStatus.READY) {
            throw new ProductException(ProductErrorCode.PRODUCT_NOT_EDITABLE);
        }
    }

    private void validateProductOwnerOrManagerOrMaster(Product product, UUID userId, String userRole) {
        if (isManagerOrMaster(userRole)) {
            return;
        }

        if (!product.getSellerId().equals(userId)) {
            throw new ProductException(ProductErrorCode.PRODUCT_FORBIDDEN);
        }
    }

    private boolean isManagerOrMaster(String userRole) {
        return "MANAGER".equalsIgnoreCase(userRole)
                || "ROLE_MANAGER".equalsIgnoreCase(userRole)
                || "MASTER".equalsIgnoreCase(userRole)
                || "ROLE_MASTER".equalsIgnoreCase(userRole);
    }

}
