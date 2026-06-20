package com.bankrupang.sanjijk.order.infrastructure.messaging.consumer;

import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.DepositForfeitedEvent;
import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.PaymentCompletedEvent;
import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.PaymentFailedEvent;
import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.RefundCompletedEvent;
import com.bankrupang.sanjijk.order.infrastructure.messaging.handler.DepositForfeitedHandler;
import com.bankrupang.sanjijk.order.infrastructure.messaging.handler.PaymentCompletedHandler;
import com.bankrupang.sanjijk.order.infrastructure.messaging.handler.PaymentFailedHandler;
import com.bankrupang.sanjijk.order.infrastructure.messaging.handler.RefundCompletedHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final PaymentCompletedHandler paymentCompletedHandler;
    private final PaymentFailedHandler paymentFailedHandler;
    private final DepositForfeitedHandler depositForfeitedHandler;
    private final RefundCompletedHandler refundCompletedHandler;

    @KafkaListener(topics = "payment-completed", groupId = "order-service")
    public void consumePaymentCompleted(PaymentCompletedEvent event) {
        log.info("[KAFKA][CONSUME] PAYMENT_COMPLETED 수신 - orderId: {}, winnerId: {}, paymentType: {}, paidAmount: {}, occurredAt: {}",
                event.orderId(), event.winnerId(), event.paymentType(), event.paidAmount(), event.occurredAt());
        paymentCompletedHandler.handle(event);
    }

    @KafkaListener(topics = "payment-failed", groupId = "order-service")
    public void consumePaymentFailed(PaymentFailedEvent event) {
        log.warn("[KAFKA][CONSUME] PAYMENT_FAILED 수신 - orderId: {}, winnerId: {}, failureMessage: {}, occurredAt: {}",
                event.orderId(), event.winnerId(), event.failureMessage(), event.occurredAt());
        paymentFailedHandler.handle(event);
    }

    @KafkaListener(topics = "deposit-forfeited", groupId = "order-service")
    public void consumeDepositForfeited(DepositForfeitedEvent event) {
        log.warn("[KAFKA][CONSUME] DEPOSIT_FORFEITED 수신 - orderId: {}, winnerId: {}, forfeitedAmount: {}, occurredAt: {}",
                event.orderId(), event.winnerId(), event.forfeitedAmount(), event.occurredAt());
        depositForfeitedHandler.handle(event);
    }

    @KafkaListener(topics = "refund-completed", groupId = "order-service")
    public void consumeRefundCompleted(RefundCompletedEvent event) {
        log.info("[KAFKA][CONSUME] REFUND_COMPLETED 수신 - orderId: {}, userId: {}, refundAmount: {}, occurredAt: {}",
                event.orderId(), event.userId(), event.refundAmount(), event.occurredAt());
        refundCompletedHandler.handle(event);
    }
}
