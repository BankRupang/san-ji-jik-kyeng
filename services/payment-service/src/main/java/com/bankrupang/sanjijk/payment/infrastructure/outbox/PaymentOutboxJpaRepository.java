package com.bankrupang.sanjijk.payment.infrastructure.outbox;

import com.bankrupang.sanjijk.payment.domian.entity.PaymentOutbox;
import com.bankrupang.sanjijk.payment.domian.enums.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PaymentOutboxJpaRepository extends JpaRepository<PaymentOutbox, UUID> {

    // PENDING + FAILED(재시도 가능) 조회
    @Query("""
            SELECT o FROM PaymentOutbox o
            WHERE (o.status = 'PENDING' OR (o.status = 'FAILED' AND o.retryCount < :maxRetryCount))
            ORDER BY o.createdAt ASC
            """)
    List<PaymentOutbox> findRetryableOutboxes(Pageable pageable, @Param("maxRetryCount") int maxRetryCount);

    // 선점: PENDING / FAILED → IN_PROGRESS (다중 인스턴스 중복 발행 방지)
    @Modifying
    @Query("""
            UPDATE PaymentOutbox o
            SET o.status = 'IN_PROGRESS'
            WHERE o.id IN :ids AND o.status IN ('PENDING', 'FAILED')
            """)
    int markInProgress(@Param("ids") List<UUID> ids);

    List<PaymentOutbox> findByIdInAndStatus(List<UUID> ids, OutboxStatus status);
}
