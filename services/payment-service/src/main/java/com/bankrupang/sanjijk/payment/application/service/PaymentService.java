package com.bankrupang.sanjijk.payment.application.service;

import com.bankrupang.sanjijk.payment.application.port.PaymentEventPublisher;
import com.bankrupang.sanjijk.payment.domian.entity.Payment;
import com.bankrupang.sanjijk.payment.domian.entity.PaymentHistory;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentStatus;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentType;
import com.bankrupang.sanjijk.payment.domian.exception.PaymentNotFoundException;
import com.bankrupang.sanjijk.payment.domian.repository.PaymentHistoryRepository;
import com.bankrupang.sanjijk.payment.domian.repository.PaymentRepository;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto.AuctionFailedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto.DepositCreatedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.consumer.dto.WinningCreatedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto.RefundCompletedEvent;
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
                    event.orderId(),
                    event.userId(),
                    null,
                    event.auctionId(),
                    event.auctionTitle(),
                    event.orderId().toString(),
                    PaymentType.REPAY,
                    event.depositAmount(),
                    null
            );
            paymentRepository.save(payment);

            paymentHistoryRepository.save(PaymentHistory.of(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getPaymentType(),
                    null,
                    PaymentStatus.READY,
                    "보증금 Payment 생성",
                    payment.getAmount(),
                    null,
                    null,
                    null   // createdBy — Kafka 컨텍스트
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
                    event.orderId(),
                    event.userId(),
                    event.sellerId(),
                    event.auctionId(),
                    event.auctionTitle(),
                    event.orderId().toString(),
                    PaymentType.NORMAL,
                    event.remainingAmount(),
                    event.finalPrice()
            );
            paymentRepository.save(payment);

            paymentHistoryRepository.save(PaymentHistory.of(
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getPaymentType(),
                    null,
                    PaymentStatus.READY,
                    "낙찰 잔금 Payment 생성",
                    payment.getAmount(),
                    null,
                    null,
                    null   // createdBy — Kafka 컨텍스트
            ));

            log.info("[PAYMENT] 낙찰 잔금 Payment 생성 완료 - paymentId: {}, orderId: {}, remainingAmount: {}",
                    payment.getId(), event.orderId(), event.remainingAmount());

            List<Payment> loserPayments = paymentRepository
                    .findByAuctionIdAndPaymentTypeAndStatusAndUserIdNot(
                            event.auctionId(),
                            PaymentType.REPAY,
                            PaymentStatus.DONE,
                            event.userId()
                    );

            log.info("[PAYMENT] 낙찰 실패자 환불 대상: {}명 - auctionId: {}", loserPayments.size(), event.auctionId());

            for (Payment loserPayment : loserPayments) {
                paymentEventPublisher.publishRefundRequest(
                        loserPayment.getOrderId(),
                        loserPayment.getId(),
                        loserPayment.getAmount(),
                        "낙찰자 외 보증금 환불"
                );
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
                            event.auctionId(),
                            PaymentType.REPAY,
                            PaymentStatus.DONE
                    );

            log.info("[PAYMENT] 유찰 환불 대상: {}명 - auctionId: {}", depositPayments.size(), event.auctionId());

            for (Payment depositPayment : depositPayments) {
                paymentEventPublisher.publishRefundRequest(
                        depositPayment.getOrderId(),
                        depositPayment.getId(),
                        depositPayment.getAmount(),
                        "유찰로 인한 보증금 환불"
                );
                log.info("[PAYMENT] 유찰 환불 요청 적재 - paymentId: {}, userId: {}, amount: {}",
                        depositPayment.getId(), depositPayment.getUserId(), depositPayment.getAmount());
            }
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
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAuctionId(),
                payment.getAuctionTitle(),
                cancelAmount,
                cancelReason,
                LocalDateTime.now()
        ));
    }

    // ================================
    // 히스토리 적재 헬퍼
    // ================================

    public void saveHistory(Payment payment, PaymentStatus prevStatus, PaymentStatus nextStatus, String reason) {
        paymentHistoryRepository.save(PaymentHistory.of(
                payment.getId(),
                payment.getOrderId(),
                payment.getPaymentType(),
                prevStatus,
                nextStatus,
                reason,
                payment.getAmount(),
                payment.getFailureCode(),
                payment.getFailureMessage(),
                null   // createdBy — Kafka/Scheduler 컨텍스트
        ));
    }

    // ================================
    // API용 (2단계에서 구현)
    // ================================

    // TODO: confirmPayment - 결제 승인
    // TODO: getPayment - 단건 조회
    // TODO: repayPayment - 잔금 재결제
}
