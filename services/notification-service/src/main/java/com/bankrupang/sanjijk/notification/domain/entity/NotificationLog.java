package com.bankrupang.sanjijk.notification.domain.entity;

import com.bankrupang.sanjijk.notification.domain.enums.NotificationStatus;
import com.bankrupang.sanjijk.notification.domain.enums.NotificationType;
import com.fasterxml.uuid.Generators;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class NotificationLog {

    @Id
    private UUID id;

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

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = Generators.timeBasedEpochGenerator().generate();
        }
    }

    public static NotificationLog create(UUID userId, NotificationType type,
                                         String title, String message,
                                         UUID referenceId, String referenceType) {
        NotificationLog log = new NotificationLog();
        log.userId = userId;
        log.type = type;
        log.title = title;
        log.message = message;
        log.status = NotificationStatus.PENDING;
        log.referenceId = referenceId;
        log.referenceType = referenceType;
        return log;
    }

    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = NotificationStatus.FAILED;
    }
}