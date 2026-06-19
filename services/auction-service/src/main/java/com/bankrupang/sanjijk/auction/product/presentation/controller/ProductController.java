package com.bankrupang.sanjijk.auction.product.presentation.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import com.bankrupang.sanjijk.auction.product.application.service.ProductService;
import com.bankrupang.sanjijk.auction.product.presentation.dto.request.ProductCreateRequest;
import com.bankrupang.sanjijk.auction.product.presentation.dto.request.ProductUpdateRequest;
import com.bankrupang.sanjijk.auction.product.presentation.dto.response.ProductCreateResponse;
import com.bankrupang.sanjijk.auction.product.presentation.dto.response.ProductResponse;
import com.bankrupang.sanjijk.auction.product.presentation.dto.response.ProductUpdateResponse;
import com.bankrupang.sanjijk.common.response.ApiResponse;
import com.bankrupang.sanjijk.common.response.PageResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProductCreateResponse>> createProduct (
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        ProductCreateResponse response = productService.createProduct(userId, userRole, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("상품이 생성되었습니다.", response));
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable UUID productId
    ) {
        ProductResponse response = productService.getProduct(productId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageResponse<ProductResponse> response = productService.getProducts(page, size);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductUpdateResponse>> updateProduct(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable UUID productId,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        ProductUpdateResponse response = productService.updateProduct(userId, userRole, productId, request);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable UUID productId
    ) {
        productService.deleteProduct(userId, userRole, productId);

        return ResponseEntity.ok(ApiResponse.ok("상품이 삭제되었습니다.", null));
    }
}
