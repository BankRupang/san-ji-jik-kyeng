package com.bankrupang.sanjijk.order.infrastructure.outbox;

import com.bankrupang.sanjijk.order.domain.entity.OrderOutbox;
import com.bankrupang.sanjijk.order.domain.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderOutboxJpaRepository extends JpaRepository<OrderOutbox, UUID> {

    List<OrderOutbox> findByStatus(OutboxStatus status);

    @Query("SELECT o FROM OrderOutbox o WHERE o.status = 'PENDING' OR (o.status = 'FAILED' AND o.retryCount < 3)")
    List<OrderOutbox> findRetryableOutboxes();
}
