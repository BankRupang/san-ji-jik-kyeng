package com.bankrupang.sanjijk.payment.application.service;

import com.bankrupang.sanjijk.payment.application.port.PaymentEventPublisher;
import com.bankrupang.sanjijk.payment.domian.entity.Payment;
import com.bankrupang.sanjijk.payment.domian.entity.PaymentHistory;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentStatus;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentType;
import com.bankrupang.sanjijk.payment.domian.exception.PaymentNotFoundException;
import com.bankrupang.sanjijk.payment.domian.exception.TossPaymentException;
import com.bankrupang.sanjijk.payment.domian.repository.PaymentHistoryRepository;
import com.bankrupang.sanjijk.payment.domian.repository.PaymentRepository;
import com.bankrupang.sanjijk.payment.infrastructure.external.PaymentConfirmTransaction;
import com.bankrupang.sanjijk.payment.infrastructure.external.dto.response.TossPaymentResponse;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto.AuctionFailedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto.DepositCreatedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto.WinningCreatedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto.RefundCompletedEvent;
import com.bankrupang.sanjijk.payment.presentation.dto.request.PaymentConfirmRequest;
import com.bankrupang.sanjijk.payment.presentation.dto.response.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final PaymentConfirmTransaction paymentConfirmTransaction;

    // ================================
    // DEPOSIT_CREATED 수신 → 보증금 Payment 생성
    // ================================

    @Transactional
    public void createDepositPayment(DepositCreatedEvent event) {
        MDC.put("traceId", UUID.randomUUID().toString());
        log.info("[PAYMENT] 보증금 Payment 생성 시작 - orderId: {}, userId: {}, auctionId: {}",
                event.orderId(), event.userId(), event.auctionId());
        try {
            if (paymentRepository.findByTossOrderId(event.orderId().toString()).isPresent()) {
                log.warn("[PAYMENT] 이미 처리된 보증금 Payment - orderId: {}", event.orderId());
                return;
            }

            Payment payment = Payment.create(
                    event.orderId(), event.userId(), null,
                    event.auctionId(), event.auctionTitle(), event.orderId().toString(),
                    PaymentType.REPAY, event.depositAmount(), null,
                    event.endAt()
            );
            paymentRepository.save(payment);

            paymentHistoryRepository.save(PaymentHistory.of(
                    payment.getId(), payment.getOrderId(), payment.getPaymentType(),
                    null, PaymentStatus.READY, "보증금 Payment 생성",
                    payment.getAmount(), null, null, null
            ));

            log.info("[PAYMENT] 보증금 Payment 생성 완료 - paymentId: {}, orderId: {}, amount: {}",
                    payment.getId(), event.orderId(), event.depositAmount());
        } finally {
            MDC.clear();
        }
    }

    // ================================
    // WINNING_CREATED 수신 → 낙찰 잔금 Payment 생성 + 낙찰 실패자 환불 요청
    // ================================

    @Transactional
    public void createWinningPayment(WinningCreatedEvent event) {
        MDC.put("traceId", UUID.randomUUID().toString());
        log.info("[PAYMENT] 낙찰 잔금 Payment 생성 시작 - orderId: {}, userId: {}, auctionId: {}",
                event.orderId(), event.userId(), event.auctionId());
        try {
            if (paymentRepository.findByTossOrderId(event.orderId().toString()).isPresent()) {
                log.warn("[PAYMENT] 이미 처리된 낙찰 Payment - orderId: {}", event.orderId());
                return;
            }

            Payment payment = Payment.create(
                    event.orderId(), event.userId(), event.sellerId(),
                    event.auctionId(), event.auctionTitle(), event.orderId().toString(),
                    PaymentType.NORMAL, event.remainingAmount(), event.finalPrice(),
                    null
            );
            paymentRepository.save(payment);

            paymentHistoryRepository.save(PaymentHistory.of(
                    payment.getId(), payment.getOrderId(), payment.getPaymentType(),
                    null, PaymentStatus.READY, "낙찰 잔금 Payment 생성",
                    payment.getAmount(), null, null, null
            ));

            log.info("[PAYMENT] 낙찰 잔금 Payment 생성 완료 - paymentId: {}, orderId: {}, remainingAmount: {}",
                    payment.getId(), event.orderId(), event.remainingAmount());

            List<Payment> loserPayments = paymentRepository
                    .findByAuctionIdAndPaymentTypeAndStatusAndUserIdNot(
                            event.auctionId(), PaymentType.REPAY, PaymentStatus.DONE, event.userId());

            log.info("[PAYMENT] 낙찰 실패자 환불 대상: {}명 - auctionId: {}", loserPayments.size(), event.auctionId());

            for (Payment loserPayment : loserPayments) {
                paymentEventPublisher.publishRefundRequest(
                        loserPayment.getOrderId(), loserPayment.getId(),
                        loserPayment.getAmount(), "낙찰자 외 보증금 환불");
                log.info("[PAYMENT] 낙찰 실패자 환불 요청 적재 - paymentId: {}, userId: {}, amount: {}",
                        loserPayment.getId(), loserPayment.getUserId(), loserPayment.getAmount());
            }
        } finally {
            MDC.clear();
        }
    }

    // ================================
    // AUCTION_FAILED 수신 → 유찰 시 전체 보증금 환불 요청
    // ================================

    @Transactional
    public void refundAllDeposits(AuctionFailedEvent event) {
        MDC.put("traceId", UUID.randomUUID().toString());
        log.info("[PAYMENT] 유찰 전체 환불 요청 시작 - auctionId: {}", event.auctionId());
        try {
            List<Payment> depositPayments = paymentRepository
                    .findByAuctionIdAndPaymentTypeAndStatus(
                            event.auctionId(), PaymentType.REPAY, PaymentStatus.DONE);

            log.info("[PAYMENT] 유찰 환불 대상: {}명 - auctionId: {}", depositPayments.size(), event.auctionId());

            for (Payment depositPayment : depositPayments) {
                paymentEventPublisher.publishRefundRequest(
                        depositPayment.getOrderId(), depositPayment.getId(),
                        depositPayment.getAmount(), "유찰로 인한 보증금 환불");
                log.info("[PAYMENT] 유찰 환불 요청 적재 - paymentId: {}, userId: {}, amount: {}",
                        depositPayment.getId(), depositPayment.getUserId(), depositPayment.getAmount());
            }
        } finally {
            MDC.clear();
        }
    }

    // ================================
    // POST /api/v1/payments/confirm → 결제 승인
    // 트랜잭션 없음 - 각 단계별 별도 트랜잭션
    // ================================

    public PaymentResponse confirmPayment(PaymentConfirmRequest request, UUID userId) {
        MDC.put("traceId", UUID.randomUUID().toString());
        log.info("[CONFIRM] 결제 승인 요청 - tossOrderId: {}, amount: {}", request.tossOrderId(), request.amount());
        try {
            // READY → IN_PROGRESS (PaymentConfirmTransaction 프록시 통해 호출)
            UUID paymentId = paymentConfirmTransaction.prepareConfirm(request, userId);

            // Toss confirm API 호출 (트랜잭션 밖)
            try {
                TossPaymentResponse tossResponse = paymentConfirmTransaction.callTossConfirm(
                        request.paymentKey(), request.tossOrderId(), request.amount());

                // 성공: DONE + Redis + Outbox
                paymentConfirmTransaction.completeConfirm(paymentId, tossResponse, userId);
                log.info("[CONFIRM] 결제 승인 완료 - paymentId: {}", paymentId);

            } catch (Exception e) {
                // 실패: ABORTED + Outbox (REQUIRES_NEW)
                String failureCode = extractFailureCode(e);
                paymentConfirmTransaction.failConfirm(paymentId, userId, failureCode, e.getMessage());
                throw e;
            }

            return PaymentResponse.from(paymentRepository.findById(paymentId)
                    .orElseThrow(PaymentNotFoundException::new));
        } finally {
            MDC.clear();
        }
    }

    // ================================
    // GET /api/v1/payments/{paymentId} → 단건 조회
    // ================================

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId, UUID userId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(PaymentNotFoundException::new);

        // 권한 검증 - 본인 결제만 조회 가능
        if (!payment.getUserId().equals(userId)) {
            log.warn("[PAYMENT] 권한 없음 - paymentId: {}, requestUserId: {}, ownerUserId: {}",
                    paymentId, userId, payment.getUserId());
            throw new PaymentNotFoundException(); // 존재 여부 노출 방지
        }

        log.info("[PAYMENT] 단건 조회 - paymentId: {}, userId: {}", paymentId, userId);
        return PaymentResponse.from(payment);
    }

    // ================================
    // POST /api/v1/payments/repay/{orderId} → 잔금 재결제
    // ================================

    @Transactional
    public PaymentResponse repayPayment(UUID orderId, UUID userId) {
        MDC.put("traceId", UUID.randomUUID().toString());
        log.info("[REPAY] 잔금 재결제 요청 - orderId: {}, userId: {}", orderId, userId);
        try {
            Payment abortedPayment = paymentRepository
                    .findByOrderIdAndPaymentTypeAndStatus(orderId, PaymentType.NORMAL, PaymentStatus.ABORTED)
                    .orElseThrow(PaymentNotFoundException::new);

            String newTossOrderId = UUID.randomUUID().toString();
            Payment newPayment = Payment.create(
                    abortedPayment.getOrderId(), abortedPayment.getUserId(), abortedPayment.getSellerId(),
                    abortedPayment.getAuctionId(), abortedPayment.getAuctionTitle(), newTossOrderId,
                    PaymentType.NORMAL, abortedPayment.getAmount(), abortedPayment.getOriginalAmount(), null
            );
            paymentRepository.save(newPayment);

            paymentHistoryRepository.save(PaymentHistory.of(
                    newPayment.getId(), newPayment.getOrderId(), newPayment.getPaymentType(),
                    null, PaymentStatus.READY, "잔금 재결제 Payment 생성",
                    newPayment.getAmount(), null, null, userId
            ));

            log.info("[REPAY] 재결제 Payment 생성 완료 - newPaymentId: {}, orderId: {}, amount: {}",
                    newPayment.getId(), orderId, newPayment.getAmount());

            return PaymentResponse.from(newPayment);
        } finally {
            MDC.clear();
        }
    }

    // ================================
    // Toss cancel 완료 후 DB 업데이트 (RefundRequestHandler에서 호출)
    // ================================

    @Transactional
    public void completeRefund(UUID paymentId, int cancelAmount, String cancelReason) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(PaymentNotFoundException::new);
        payment.refund(cancelAmount, cancelReason);
        saveHistory(payment, PaymentStatus.DONE, PaymentStatus.CANCELED, cancelReason);
        paymentEventPublisher.publishRefundCompleted(new RefundCompletedEvent(
                payment.getId(), payment.getOrderId(), payment.getUserId(),
                payment.getAuctionId(), payment.getAuctionTitle(),
                cancelAmount, cancelReason, LocalDateTime.now()
        ));
    }

    // ================================
    // 히스토리 적재 헬퍼
    // ================================

    public void saveHistory(Payment payment, PaymentStatus prevStatus, PaymentStatus nextStatus, String reason) {
        paymentHistoryRepository.save(PaymentHistory.of(
                payment.getId(), payment.getOrderId(), payment.getPaymentType(),
                prevStatus, nextStatus, reason, payment.getAmount(),
                payment.getFailureCode(), payment.getFailureMessage(), null
        ));
    }

    // ================================
    // Toss 응답에서 failureCode 추출
    // ================================

    private String extractFailureCode(Exception e) {
        if (e instanceof TossPaymentException tossEx) {
            return tossEx.getCode() != null ? tossEx.getCode() : "UNKNOWN";
        }
        return "UNKNOWN";
    }
}
