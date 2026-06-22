package com.bankrupang.sanjijk.notification.application.service;

import com.bankrupang.sanjijk.notification.application.port.NotificationSendPort;
import com.bankrupang.sanjijk.notification.domain.entity.NotificationLog;
import com.bankrupang.sanjijk.notification.domain.enums.NotificationStatus;
import com.bankrupang.sanjijk.notification.domain.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRetryService {

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationSendPort notificationSendPort;
    private final NotificationEventService notificationEventService;

    public void retryPending() {
        Pageable pageable = PageRequest.of(0, 50);
        List<NotificationLog> pendingLogs = notificationLogRepository
                .findByStatusAndNextRetryAtBefore(NotificationStatus.PENDING, LocalDateTime.now(), pageable);

        if (pendingLogs.isEmpty()) return;

        log.info("재시도 대상 알림 {}건 처리 시작", pendingLogs.size());

        for (NotificationLog notificationLog : pendingLogs) {
            try {
                notificationSendPort.send(notificationLog.getSlackId(), notificationLog.getMessage());
                notificationEventService.markSent(notificationLog.getId());
                log.info("재시도 발송 성공 logId={}", notificationLog.getId());
            } catch (Exception e) {
                log.error("재시도 발송 실패 logId={}, error={}", notificationLog.getId(), e.getMessage());
                notificationEventService.scheduleRetry(notificationLog.getId());
            }
        }
    }
}
