package com.bankrupang.sanjijk.notification.domain.entity;

import com.bankrupang.sanjijk.notification.domain.enums.NotificationStatus;
import com.bankrupang.sanjijk.notification.domain.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationLog 도메인 단위 테스트")
class NotificationLogTest {

    private NotificationLog createLog() {
        return NotificationLog.create(
                UUID.randomUUID(), NotificationType.AUCTION_WON,
                "낙찰 확정!", "메시지", UUID.randomUUID(), "AUCTION", "U_TEST_SLACK"
        );
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("생성 시 PENDING 상태, retryCount=0, nextRetryAt=null")
        void initialState() {
            // given
            UUID userId = UUID.randomUUID();

            // when
            NotificationLog log = NotificationLog.create(
                    userId, NotificationType.AUCTION_WON,
                    "낙찰 확정!", "메시지", UUID.randomUUID(), "AUCTION", "U_TEST_SLACK"
            );

            // then
            assertThat(log.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(log.getRetryCount()).isZero();
            assertThat(log.getNextRetryAt()).isNull();
            assertThat(log.getUserId()).isEqualTo(userId);
            assertThat(log.getSlackId()).isEqualTo("U_TEST_SLACK");
        }
    }

    @Nested
    @DisplayName("scheduleRetry()")
    class ScheduleRetry {

        @Test
        @DisplayName("첫 재시도: retryCount=1, PENDING 유지, nextRetryAt 1분 후 설정")
        void firstRetry() {
            // given
            NotificationLog log = createLog();
            LocalDateTime before = LocalDateTime.now();

            // when
            log.scheduleRetry();

            // then
            assertThat(log.getRetryCount()).isEqualTo(1);
            assertThat(log.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(log.getNextRetryAt()).isBetween(
                    before.plusMinutes(1).minusSeconds(1),
                    before.plusMinutes(1).plusSeconds(1)
            );
        }

        @Test
        @DisplayName("지수 백오프: 1→2→4→8→16분 순으로 nextRetryAt 증가")
        void exponentialBackoff() {
            // given
            NotificationLog log = createLog();
            long[] expectedMinutes = {1, 2, 4, 8, 16};

            for (int i = 0; i < 5; i++) {
                // when
                LocalDateTime before = LocalDateTime.now();
                log.scheduleRetry();

                // then
                assertThat(log.getRetryCount()).isEqualTo(i + 1);
                assertThat(log.getNextRetryAt()).isBetween(
                        before.plusMinutes(expectedMinutes[i]).minusSeconds(1),
                        before.plusMinutes(expectedMinutes[i]).plusSeconds(1)
                );
            }
        }

        @Test
        @DisplayName("MAX_RETRY(5회) 소진 후 6번째 호출 시 FAILED 전환")
        void failAfterMaxRetry() {
            // given
            NotificationLog log = createLog();
            for (int i = 0; i < 5; i++) {
                log.scheduleRetry();
            }

            // when
            log.scheduleRetry();

            // then
            assertThat(log.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(log.getNextRetryAt()).isNull();
        }
    }

    @Nested
    @DisplayName("markSent()")
    class MarkSent {

        @Test
        @DisplayName("SENT 상태로 전환, sentAt 설정, nextRetryAt 제거")
        void markSent() {
            // given
            NotificationLog log = createLog();

            // when
            log.markSent();

            // then
            assertThat(log.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(log.getSentAt()).isNotNull();
            assertThat(log.getNextRetryAt()).isNull();
        }
    }

    @Nested
    @DisplayName("markFailed()")
    class MarkFailed {

        @Test
        @DisplayName("FAILED 상태로 전환, nextRetryAt 제거")
        void markFailed() {
            // given
            NotificationLog log = createLog();

            // when
            log.markFailed();

            // then
            assertThat(log.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(log.getNextRetryAt()).isNull();
        }
    }
}
