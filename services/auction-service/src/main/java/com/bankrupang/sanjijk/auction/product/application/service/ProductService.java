package com.bankrupang.sanjijk.auction.product.application.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.bankrupang.sanjijk.auction.product.domain.entity.Product;
import com.bankrupang.sanjijk.auction.product.domain.repository.ProductRepository;
import com.bankrupang.sanjijk.auction.product.exception.ProductErrorCode;
import com.bankrupang.sanjijk.auction.product.exception.ProductException;
import com.bankrupang.sanjijk.auction.product.presentation.dto.request.ProductCreateRequest;
import com.bankrupang.sanjijk.auction.product.presentation.dto.response.ProductCreateResponse;

@Service
@RequiredArgsConstructor
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

}
