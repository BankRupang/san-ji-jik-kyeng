package com.bankrupang.sanjijk.payment.application.service;

import com.bankrupang.sanjijk.payment.domian.entity.Payment;
import com.bankrupang.sanjijk.payment.domian.enums.PaymentStatus;
import com.bankrupang.sanjijk.payment.domian.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentScheduler {

    private final PaymentRepository paymentRepository;
    private final PaymentSchedulerTransaction paymentSchedulerTransaction;

    private static final int EXPIRE_MINUTES = 15;
    private static final int BATCH_SIZE = 100;

    // 1분마다 실행 - READY 상태에서 15분 초과한 Payment 만료 처리
    @Scheduled(fixedDelay = 60_000)
    public void expireReadyPayments() {
        LocalDateTime expiredBefore = LocalDateTime.now().minusMinutes(EXPIRE_MINUTES);

        Pageable pageable = PageRequest.of(0, BATCH_SIZE);

        while (true) {
            List<Payment> expiredPayments = paymentRepository
                    .findExpiredPayments(PaymentStatus.READY, expiredBefore, pageable);

            if (expiredPayments.isEmpty()) break;

            log.info("[EXPIRE] 만료 대상 Payment: {}건", expiredPayments.size());

            for (Payment payment : expiredPayments) {
                try {
                    paymentSchedulerTransaction.expireOne(payment);
                } catch (Exception e) {
                    log.error("[EXPIRE] Payment 만료 처리 실패 - paymentId: {}, error: {}",
                            payment.getId(), e.getMessage(), e);
                }
            }
        }
    }
}
