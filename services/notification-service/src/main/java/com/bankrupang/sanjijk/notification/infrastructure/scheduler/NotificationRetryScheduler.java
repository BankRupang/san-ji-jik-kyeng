package com.bankrupang.sanjijk.notification.infrastructure.scheduler;

import com.bankrupang.sanjijk.notification.application.service.NotificationRetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private final NotificationRetryService notificationRetryService;

    @Scheduled(fixedDelayString = "${notification.scheduler.fixed-delay-ms:60000}")
    public void retryPendingNotifications() {
        notificationRetryService.retryPending();
    }
}
