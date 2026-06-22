package com.bankrupang.sanjijk.payment.infrastructure.external;

import com.bankrupang.sanjijk.payment.application.port.PaymentEventPublisher;
import com.bankrupang.sanjijk.payment.domian.entity.Payment;
import com.bankrupang.sanjijk.payment.domian.entity.PaymentHistory;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentStatus;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentType;
import com.bankrupang.sanjijk.payment.domian.repository.PaymentHistoryRepository;
import com.bankrupang.sanjijk.payment.domian.repository.PaymentRepository;
import com.bankrupang.sanjijk.payment.infrastructure.external.dto.request.TossConfirmRequest;
import com.bankrupang.sanjijk.payment.infrastructure.external.dto.response.TossPaymentResponse;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto.PaymentCompletedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentConfirmTransaction {

    private final TossPaymentsClient tossPaymentsClient;
    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final RedisTemplate<String, String> redisTemplate;

    // Toss confirm API 호출 (트랜잭션 밖)
    public TossPaymentResponse callTossConfirm(String paymentKey, String tossOrderId, int amount) {
        log.info("[CONFIRM] Toss confirm API 호출 - tossOrderId: {}, amount: {}", tossOrderId, amount);
        return tossPaymentsClient.confirm(new TossConfirmRequest(paymentKey, tossOrderId, amount));
    }

    // 결제 성공 DB 업데이트 + Redis write (REPAY만) + Outbox 적재
    @Transactional
    public void completeConfirm(UUID paymentId, TossPaymentResponse tossResponse,
                                UUID auctionId, UUID userId, LocalDateTime endAt) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        TossPaymentResponse.Card card = tossResponse.card();
        payment.confirm(
                tossResponse.paymentKey(),
                card != null ? card.issuerCode() : null,
                card != null ? card.number() : null,
                card != null ? card.cardType() : null,
                card != null ? card.installmentPlanMonths() : 0,
                tossResponse.receipt() != null ? tossResponse.receipt().url() : null,
                OffsetDateTime.parse(tossResponse.approvedAt()).toLocalDateTime()
        );

        // 히스토리 적재 (IN_PROGRESS → DONE)
        paymentHistoryRepository.save(PaymentHistory.of(
                payment.getId(), payment.getOrderId(), payment.getPaymentType(),
                PaymentStatus.IN_PROGRESS, PaymentStatus.DONE, "결제 승인 완료",
                payment.getAmount(), null, null, userId
        ));

        // 보증금(REPAY)이면 Redis write
        if (payment.getPaymentType() == PaymentType.REPAY) {
            if (endAt == null) {
                log.warn("[CONFIRM] 보증금 결제인데 endAt이 null - Redis 키 등록 생략 - paymentId: {}", paymentId);
            } else {
                String redisKey = "auction:" + auctionId + ":deposit:" + userId;
                long ttlSeconds = Duration.between(LocalDateTime.now(), endAt.plusHours(2)).getSeconds();
                redisTemplate.opsForValue().set(redisKey, "true", Duration.ofSeconds(Math.max(ttlSeconds, 1)));
                log.info("[CONFIRM] Redis 보증금 키 등록 - key: {}, ttl: {}s", redisKey, ttlSeconds);
            }
        }

        // PAYMENT_COMPLETED Outbox 적재
        paymentEventPublisher.publishPaymentCompleted(new PaymentCompletedEvent(
                payment.getOrderId(),
                payment.getAuctionId(),
                payment.getAuctionTitle(),
                payment.getUserId(),
                payment.getSellerId(),
                payment.getOriginalAmount(),
                payment.getAmount(),
                payment.getPaymentType().name(),
                LocalDateTime.now()
        ));

        log.info("[CONFIRM] 결제 승인 완료 - paymentId: {}, paymentType: {}", paymentId, payment.getPaymentType());
    }

    // 결제 실패 DB 업데이트 + Outbox 적재
    @Transactional
    public void failConfirm(UUID paymentId, UUID userId, String failureCode, String failureMessage) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        payment.fail(failureCode, failureMessage);

        // 히스토리 적재 (IN_PROGRESS → ABORTED)
        paymentHistoryRepository.save(PaymentHistory.of(
                payment.getId(), payment.getOrderId(), payment.getPaymentType(),
                PaymentStatus.IN_PROGRESS, PaymentStatus.ABORTED, "결제 승인 실패",
                payment.getAmount(), failureCode, failureMessage, userId
        ));

        // PAYMENT_FAILED Outbox 적재
        paymentEventPublisher.publishPaymentFailed(new PaymentFailedEvent(
                payment.getOrderId(),
                payment.getAuctionId(),
                payment.getAuctionTitle(),
                payment.getUserId(),
                payment.getSellerId(),
                payment.getOriginalAmount(),
                failureMessage,
                LocalDateTime.now()
        ));

        log.error("[CONFIRM] 결제 승인 실패 - paymentId: {}, failureCode: {}, failureMessage: {}",
                paymentId, failureCode, failureMessage);
    }
}
