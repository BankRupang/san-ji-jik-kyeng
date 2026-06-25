package com.bankrupang.sanjijk.auction.product.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.bankrupang.sanjijk.auction.product.domain.entity.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Optional<Product> findByIdAndDeletedAtIsNull(UUID id);

    Page<Product> findAllByDeletedAtIsNull(Pageable pageable);

    List<Product> findAllByIdInAndDeletedAtIsNull(Collection<UUID> ids);

}
