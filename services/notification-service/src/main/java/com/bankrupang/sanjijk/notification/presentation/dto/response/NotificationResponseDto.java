package com.bankrupang.sanjijk.notification.presentation.dto.response;

import com.bankrupang.sanjijk.notification.domain.entity.NotificationLog;
import com.bankrupang.sanjijk.notification.domain.enums.NotificationStatus;
import com.bankrupang.sanjijk.notification.domain.enums.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class NotificationResponseDto {

    private UUID id;
    private UUID userId;
    private NotificationType type;
    private String title;
    private String message;
    private NotificationStatus status;
    private UUID referenceId;
    private String referenceType;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;

    public static NotificationResponseDto from(NotificationLog log) {
        return NotificationResponseDto.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .type(log.getType())
                .title(log.getTitle())
                .message(log.getMessage())
                .status(log.getStatus())
                .referenceId(log.getReferenceId())
                .referenceType(log.getReferenceType())
                .createdAt(log.getCreatedAt())
                .sentAt(log.getSentAt())
                .build();
    }
}
