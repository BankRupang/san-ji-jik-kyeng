package com.bankrupang.sanjijk.notification.domain.entity;

import com.bankrupang.sanjijk.common.entity.BaseEntity;
import com.bankrupang.sanjijk.notification.domain.enums.NotificationStatus;
import com.bankrupang.sanjijk.notification.domain.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLog extends BaseEntity {

    private static final int MAX_RETRY = 5;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    private UUID referenceId;

    private String referenceType;

    @Column(nullable = false)
    private String slackId;

    private LocalDateTime sentAt;

    @Column(nullable = false)
    private int retryCount = 0;

    private LocalDateTime nextRetryAt;

    public static NotificationLog create(UUID userId, NotificationType type,
                                         String title, String message,
                                         UUID referenceId, String referenceType,
                                         String slackId) {
        NotificationLog log = new NotificationLog();
        log.userId = userId;
        log.type = type;
        log.title = title;
        log.message = message;
        log.status = NotificationStatus.PENDING;
        log.referenceId = referenceId;
        log.referenceType = referenceType;
        log.slackId = slackId;
        log.nextRetryAt = null;
        return log;
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.nextRetryAt = null;
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
        this.nextRetryAt = null;
    }

    public void scheduleRetry() {
        if (this.retryCount >= MAX_RETRY) {
            markFailed();
            return;
        }
        this.retryCount++;
        long minutes = (long) Math.pow(2, this.retryCount - 1);
        this.nextRetryAt = LocalDateTime.now().plusMinutes(minutes);
    }
}