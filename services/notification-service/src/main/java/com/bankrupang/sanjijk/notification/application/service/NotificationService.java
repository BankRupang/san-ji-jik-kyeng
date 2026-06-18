package com.bankrupang.sanjijk.notification.application.service;

import com.bankrupang.sanjijk.common.exception.BaseException;
import com.bankrupang.sanjijk.notification.domain.entity.NotificationLog;
import com.bankrupang.sanjijk.notification.domain.repository.NotificationLogRepository;
import com.bankrupang.sanjijk.notification.exception.NotificationErrorCode;
import com.bankrupang.sanjijk.notification.presentation.dto.response.NotificationResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationLogRepository notificationLogRepository;

    public Page<NotificationResponseDto> getMyNotifications(UUID userId, Pageable pageable) {
        return notificationLogRepository.findByUserId(userId, pageable)
                .map(NotificationResponseDto::from);
    }

    public NotificationResponseDto getNotification(UUID userId, UUID notificationId) {
        NotificationLog log = notificationLogRepository.findById(notificationId)
                .orElseThrow(() -> new BaseException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));

        if (!log.getUserId().equals(userId)) {
            throw new BaseException(NotificationErrorCode.NOTIFICATION_ACCESS_DENIED);
        }

        return NotificationResponseDto.from(log);
    }

    public Page<NotificationResponseDto> getAllNotifications(Pageable pageable) {
        return notificationLogRepository.findAll(pageable)
                .map(NotificationResponseDto::from);
    }
}
