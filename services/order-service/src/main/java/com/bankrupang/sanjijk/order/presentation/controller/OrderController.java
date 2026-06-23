package com.bankrupang.sanjijk.order.presentation.controller;

import com.bankrupang.sanjijk.common.response.ApiResponse;
import com.bankrupang.sanjijk.order.application.service.OrderService;
import com.bankrupang.sanjijk.order.presentation.dto.request.OrderDepositCreateRequest;
import com.bankrupang.sanjijk.order.presentation.dto.response.OrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.Page;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/deposit")
    public ResponseEntity<ApiResponse<OrderResponse>> createDepositOrder(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody OrderDepositCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.create(orderService.createDepositOrder(userId, request)));
    }

    @GetMapping("/deposit/me")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getMyDepositOrders(
            @RequestHeader("X-User-Id") UUID userId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getMyDepositOrders(userId, pageable)));
    }

    @GetMapping("/winning/me")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getMyWinningOrders(
            @RequestHeader("X-User-Id") UUID userId,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok(orderService.getMyWinningOrders(userId, pageable)));
    }
}
