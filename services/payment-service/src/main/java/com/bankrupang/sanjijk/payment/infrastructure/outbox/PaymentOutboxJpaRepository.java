package com.bankrupang.sanjijk.payment.infrastructure.outbox;

import com.bankrupang.sanjijk.payment.domian.entity.PaymentOutbox;
import com.bankrupang.sanjijk.payment.domian.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentOutboxJpaRepository extends JpaRepository<PaymentOutbox, UUID> {

    // 단순 조회용: 상태 조회
    List<PaymentOutbox> findByStatus(OutboxStatus status);

    // 릴레이 대상 조회: PENDING이거나 FAILED지만 재시도 가능한 것만
    @Query("SELECT o FROM PaymentOutbox o WHERE o.status = 'PENDING' OR (o.status = 'FAILED' AND o.retryCount < :maxRetry) ORDER BY o.createdAt ASC")
    List<PaymentOutbox> findRetryableOutboxes(Pageable pageable, @Param("maxRetry") int maxRetry);
}
