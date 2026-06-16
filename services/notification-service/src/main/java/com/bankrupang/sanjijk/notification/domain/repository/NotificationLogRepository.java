package com.bankrupang.sanjijk.notification.domain.repository;

import com.bankrupang.sanjijk.notification.domain.entity.NotificationLog;
import com.bankrupang.sanjijk.notification.domain.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Page<NotificationLog> findByUserId(UUID userId, Pageable pageable);

    List<NotificationLog> findByStatusAndSentAtIsNullAndCreatedAtBefore(
            NotificationStatus status, LocalDateTime before);
}