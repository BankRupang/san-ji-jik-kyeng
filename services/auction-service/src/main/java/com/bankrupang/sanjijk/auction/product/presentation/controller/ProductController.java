package com.bankrupang.sanjijk.auction.product.presentation.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Product API", description = "상품 관리 API")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SELLER', 'MASTER')")
    @Operation(summary = "상품 등록", description = "판매자가 새로운 경매 대상 상품을 등록합니다.")
    public ResponseEntity<ApiResponse<ProductCreateResponse>> createProduct (
            @RequestHeader("X-User-Id") @Parameter(description = "사용자 ID") UUID userId,
            @Valid @RequestBody ProductCreateRequest request
    ) {
        ProductCreateResponse response = productService.createProduct(userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("상품이 생성되었습니다.", response));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "상품 단건 조회", description = "특정 상품의 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(
            @PathVariable @Parameter(description = "상품 ID") UUID productId
    ) {
        ProductResponse response = productService.getProduct(productId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    @Operation(summary = "상품 목록 조회", description = "전체 상품 리스트를 페이징하여 조회합니다.")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getProducts(
            @RequestParam(defaultValue = "0") @Parameter(description = "페이지 번호") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "페이지 크기") int size
    ) {
        PageResponse<ProductResponse> response = productService.getProducts(page, size);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{productId}")
    @PreAuthorize("hasAnyRole('SELLER', 'MANAGER', 'MASTER')")
    @Operation(summary = "상품 수정", description = "등록된 상품의 이름, 설명, 수량을 수정합니다.")
    public ResponseEntity<ApiResponse<ProductUpdateResponse>> updateProduct(
            @RequestHeader("X-User-Id") @Parameter(description = "사용자 ID") UUID userId,
            @RequestHeader("X-User-Role") @Parameter(description = "사용자 역할") String userRole,
            @PathVariable @Parameter(description = "상품 ID") UUID productId,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        ProductUpdateResponse response = productService.updateProduct(userId, userRole, productId, request);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{productId}")
    @PreAuthorize("hasAnyRole('SELLER', 'MANAGER', 'MASTER')")
    @Operation(summary = "상품 삭제", description = "상품을 소프트 삭제 처리합니다.")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @RequestHeader("X-User-Id") @Parameter(description = "사용자 ID") UUID userId,
            @RequestHeader("X-User-Role") @Parameter(description = "사용자 역할") String userRole,
            @PathVariable @Parameter(description = "상품 ID") UUID productId
    ) {
        productService.deleteProduct(userId, userRole, productId);

        return ResponseEntity.ok(ApiResponse.ok("상품이 삭제되었습니다.", null));
    }
}
