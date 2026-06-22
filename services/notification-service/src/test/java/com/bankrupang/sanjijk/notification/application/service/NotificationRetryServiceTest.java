package com.bankrupang.sanjijk.notification.application.service;

import com.bankrupang.sanjijk.notification.application.port.NotificationSendPort;
import com.bankrupang.sanjijk.notification.domain.entity.NotificationLog;
import com.bankrupang.sanjijk.notification.domain.enums.NotificationType;
import com.bankrupang.sanjijk.notification.domain.repository.NotificationLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationRetryService 단위 테스트")
class NotificationRetryServiceTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private NotificationSendPort notificationSendPort;

    @Mock
    private NotificationEventService notificationEventService;

    @InjectMocks
    private NotificationRetryService notificationRetryService;

    @Nested
    @DisplayName("retryPending()")
    class RetryPending {

        @Test
        @DisplayName("재시도 대상 없으면 발송 및 상태 변경 미호출")
        void noTargets() {
            // given
            given(notificationLogRepository.findByStatusAndNextRetryAtBefore(any(), any(), any()))
                    .willReturn(List.of());

            // when
            notificationRetryService.retryPending();

            // then
            then(notificationSendPort).shouldHaveNoInteractions();
            then(notificationEventService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("발송 성공 시 markSent 호출, scheduleRetry 미호출")
        void sendSuccess() {
            // given
            NotificationLog log = createLog();
            given(notificationLogRepository.findByStatusAndNextRetryAtBefore(any(), any(), any()))
                    .willReturn(List.of(log));

            // when
            notificationRetryService.retryPending();

            // then
            then(notificationSendPort).should().send(log.getSlackId(), log.getMessage());
            then(notificationEventService).should().markSent(log.getId());
            then(notificationEventService).should(never()).scheduleRetry(any());
        }

        @Test
        @DisplayName("발송 실패 시 scheduleRetry 호출, markSent 미호출")
        void sendFailure() {
            // given
            NotificationLog log = createLog();
            given(notificationLogRepository.findByStatusAndNextRetryAtBefore(any(), any(), any()))
                    .willReturn(List.of(log));
            willThrow(new RuntimeException("Slack 오류"))
                    .given(notificationSendPort).send(anyString(), anyString());

            // when
            notificationRetryService.retryPending();

            // then
            then(notificationEventService).should().scheduleRetry(log.getId());
            then(notificationEventService).should(never()).markSent(any());
        }

        @Test
        @DisplayName("발송 성공 후 markSent 실패 시 scheduleRetry 호출 (at-least-once)")
        void markSentFailure() {
            // given
            NotificationLog log = createLog();
            given(notificationLogRepository.findByStatusAndNextRetryAtBefore(any(), any(), any()))
                    .willReturn(List.of(log));
            willThrow(new RuntimeException("DB 오류"))
                    .given(notificationEventService).markSent(any());

            // when
            notificationRetryService.retryPending();

            // then
            then(notificationSendPort).should().send(anyString(), anyString());
            then(notificationEventService).should().scheduleRetry(log.getId());
        }

        @Test
        @DisplayName("여러 건 중 일부 실패해도 나머지 정상 처리")
        void partialFailure() {
            // given
            NotificationLog successLog = createLog("U_SUCCESS_SLACK");
            NotificationLog failLog = createLog("U_FAIL_SLACK");
            given(notificationLogRepository.findByStatusAndNextRetryAtBefore(any(), any(), any()))
                    .willReturn(List.of(successLog, failLog));
            willAnswer(invocation -> {
                if ("U_FAIL_SLACK".equals(invocation.<String>getArgument(0))) {
                    throw new RuntimeException("Slack 오류");
                }
                return null;
            }).given(notificationSendPort).send(anyString(), anyString());

            // when
            notificationRetryService.retryPending();

            // then
            then(notificationEventService).should().markSent(successLog.getId());
            then(notificationEventService).should().scheduleRetry(failLog.getId());
        }
    }

    private NotificationLog createLog() {
        return createLog("U_TEST_SLACK");
    }

    private NotificationLog createLog(String slackId) {
        NotificationLog log = NotificationLog.create(
                UUID.randomUUID(), NotificationType.AUCTION_WON,
                "낙찰 확정!", "메시지", UUID.randomUUID(), "AUCTION", slackId
        );
        ReflectionTestUtils.setField(log, "id", UUID.randomUUID());
        return log;
    }
}
