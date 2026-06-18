package com.bankrupang.sanjijk.auction.product.domain.entity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.bankrupang.sanjijk.common.entity.BaseEntity;

@Entity
@Table(name = "p_products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Column(name = "seller_id", nullable = false)
    private UUID sellerId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String quantity;

    public static Product create(UUID sellerId, String name, String description, String quantity) {

        Product product = new Product();

        product.sellerId = sellerId;
        product.name = name;
        product.description = description;
        product.quantity = quantity;

        return product;
    }

}
