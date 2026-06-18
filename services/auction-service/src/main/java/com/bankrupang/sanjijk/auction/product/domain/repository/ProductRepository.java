package com.bankrupang.sanjijk.auction.product.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bankrupang.sanjijk.auction.product.domain.entity.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsByIdAndDeletedAtIsNull(UUID id);

    Optional<Product> findByIdAndDeletedAtIsNull(UUID id);

    Page<Product> findAllByDeletedAtIsNull(Pageable pageable);

    boolean existsByIdAndSellerIdAndDeletedAtIsNull(UUID id, UUID sellerId);

    Optional<Product> findByIdAndSellerIdAndDeletedAtIsNull(UUID id, UUID sellerId);

}
