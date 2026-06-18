package com.bankrupang.sanjijk.auction.product.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bankrupang.sanjijk.auction.product.domain.entity.Product;

public record ProductDetailResponse(
        UUID productId,
        UUID sellerId,
        String name,
        String description,
        String quantity,
        LocalDateTime createdAt
) {
    public static ProductDetailResponse from(Product product) {
        return new ProductDetailResponse(
                product.getId(),
                product.getSellerId(),
                product.getName(),
                product.getDescription(),
                product.getQuantity(),
                product.getCreatedAt()
        );
    }

}
