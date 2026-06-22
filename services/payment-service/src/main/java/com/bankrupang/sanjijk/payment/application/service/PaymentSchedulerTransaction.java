package com.bankrupang.sanjijk.payment.application.service;

import com.bankrupang.sanjijk.payment.domian.entity.Payment;
import com.bankrupang.sanjijk.payment.domian.entity.PaymentHistory;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentStatus;
import com.bankrupang.sanjijk.payment.domian.exception.PaymentNotFoundException;
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
        // detached entity 재사용 대신 DB에서 현재 상태 fresh load
        // → 조회 후 confirmPayment 호출로 IN_PROGRESS 변경된 경우 덮어쓰기 방지
        Payment fresh = paymentRepository.findById(payment.getId())
                .orElseThrow(PaymentNotFoundException::new);
        fresh.expire(); // DB가 IN_PROGRESS면 InvalidPaymentStatusException → 외부 catch 처리
        paymentHistoryRepository.save(PaymentHistory.of(
                fresh.getId(), fresh.getOrderId(), fresh.getPaymentType(),
                PaymentStatus.READY, PaymentStatus.EXPIRED, "결제 유효시간 초과 (15분)",
                fresh.getAmount(), null, null, null
        ));
        // dirty checking으로 자동 반영 - paymentRepository.save() 불필요
        log.info("[EXPIRE] Payment 만료 처리 완료 - paymentId: {}, paymentType: {}, requestedAt: {}",
                fresh.getId(), fresh.getPaymentType(), fresh.getRequestedAt());
    }
}
