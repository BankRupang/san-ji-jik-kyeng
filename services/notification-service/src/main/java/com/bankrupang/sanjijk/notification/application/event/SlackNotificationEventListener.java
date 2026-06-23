package com.bankrupang.sanjijk.notification.application.event;

import com.bankrupang.sanjijk.notification.application.port.NotificationSendPort;
import com.bankrupang.sanjijk.notification.application.service.NotificationEventService;
import com.bankrupang.sanjijk.notification.domain.entity.NotificationLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotificationEventListener {

    private final NotificationSendPort notificationSendPort;
    private final NotificationEventService notificationEventService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(SlackNotificationEvent event) {
        NotificationLog notificationLog = event.getNotificationLog();
        // at-least-once 보장 정책: send 성공 후 markSent DB 기록 실패 시 재시도로 중복 발송될 수 있음.
        // 알림 서비스 특성상 exactly-once보다 유실 방지를 우선한다.
        try {
            notificationSendPort.send(notificationLog.getSlackId(), notificationLog.getMessage());
            notificationEventService.markSent(notificationLog.getId());
            log.info("Slack 발송 성공 logId={}", notificationLog.getId());
        } catch (Exception e) {
            log.error("Slack 발송 실패 logId={}, error={}", notificationLog.getId(), e.getMessage());
            notificationEventService.scheduleRetry(notificationLog.getId());
        }
    }
}
