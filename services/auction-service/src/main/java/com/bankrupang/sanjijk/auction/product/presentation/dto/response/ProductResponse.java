package com.bankrupang.sanjijk.auction.product.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.bankrupang.sanjijk.auction.product.domain.entity.Product;

public record ProductResponse(
        UUID productId,
        UUID sellerId,
        String name,
        String description,
        String quantity,
        LocalDateTime createdAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSellerId(),
                product.getName(),
                product.getDescription(),
                product.getQuantity(),
                product.getCreatedAt()
        );
    }

}
