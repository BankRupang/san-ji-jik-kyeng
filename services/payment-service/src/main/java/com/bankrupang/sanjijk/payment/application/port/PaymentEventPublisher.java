package com.bankrupang.sanjijk.payment.application.port;

import com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto.DepositForfeitedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto.PaymentCompletedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto.PaymentFailedEvent;
import com.bankrupang.sanjijk.payment.infrastructure.messaging.producer.dto.RefundCompletedEvent;

import java.util.UUID;

public interface PaymentEventPublisher {

    void publishPaymentCompleted(PaymentCompletedEvent event);

    void publishPaymentFailed(PaymentFailedEvent event);

    void publishRefundCompleted(RefundCompletedEvent event);

    void publishDepositForfeited(DepositForfeitedEvent event);

    // Toss cancel API 호출 요청 → Outbox에 REFUND_REQUEST 적재
    void publishRefundRequest(UUID orderId, UUID paymentId, int cancelAmount, String cancelReason);
}
