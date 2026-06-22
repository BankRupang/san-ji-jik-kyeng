package com.bankrupang.sanjijk.payment.application.service;

import com.bankrupang.sanjijk.payment.domian.entity.Payment;
import com.bankrupang.sanjijk.payment.domian.entity.PaymentHistory;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentStatus;
import com.bankrupang.sanjijk.payment.domian.repository.PaymentHistoryRepository;
import com.bankrupang.sanjijk.payment.domian.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentSchedulerTransaction {

    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;

    // 개별 Payment 만료 처리 (REQUIRES_NEW - 한 건 실패가 다른 건에 영향 없도록)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOne(Payment payment) {
        payment.expire();
        paymentHistoryRepository.save(PaymentHistory.of(
                payment.getId(), payment.getOrderId(), payment.getPaymentType(),
                PaymentStatus.READY, PaymentStatus.EXPIRED, "결제 유효시간 초과 (15분)",
                payment.getAmount(), null, null, null
        ));
        paymentRepository.save(payment);
        log.info("[EXPIRE] Payment 만료 처리 완료 - paymentId: {}, paymentType: {}, requestedAt: {}",
                payment.getId(), payment.getPaymentType(), payment.getRequestedAt());
    }
}
