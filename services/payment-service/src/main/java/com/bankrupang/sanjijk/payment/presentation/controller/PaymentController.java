package com.bankrupang.sanjijk.payment.presentation.controller;

import com.bankrupang.sanjijk.payment.application.service.PaymentService;
import com.bankrupang.sanjijk.payment.presentation.dto.request.PaymentConfirmRequest;
import com.bankrupang.sanjijk.payment.presentation.dto.response.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // 결제 승인 (보증금 / 낙찰 잔금 공통)
    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponse> confirmPayment(
            @RequestBody PaymentConfirmRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt
    ) {
        log.info("[API] POST /confirm - userId: {}, tossOrderId: {}", userId, request.tossOrderId());
        return ResponseEntity.ok(paymentService.confirmPayment(request, userId, endAt));
    }

    // 단건 조회
    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable UUID paymentId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        log.info("[API] GET /{} - userId: {}", paymentId, userId);
        return ResponseEntity.ok(paymentService.getPayment(paymentId, userId));
    }

    // 잔금 재결제 (결제 실패 후 15분 내 다른 카드로 재결제)
    @PostMapping("/repay/{orderId}")
    public ResponseEntity<PaymentResponse> repayPayment(
            @PathVariable UUID orderId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        log.info("[API] POST /repay/{} - userId: {}", orderId, userId);
        return ResponseEntity.ok(paymentService.repayPayment(orderId, userId));
    }
}
