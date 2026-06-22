package com.bankrupang.sanjijk.auction.auction.infrastructure.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@DisplayName("AuctionScheduleManager 테스트")
@ExtendWith(MockitoExtension.class)
class AuctionScheduleManagerTest {

    @Mock
    private ThreadPoolTaskScheduler taskScheduler;

    @Nested
    @DisplayName("scheduleStartJob()")
    class ScheduleStartJob {

        @Test
        @DisplayName("성공 - 경매 시작 잡을 등록한다")
        void success() {
            // given
            AuctionScheduleManager scheduleManager = new AuctionScheduleManager(taskScheduler);
            UUID auctionId = UUID.randomUUID();
            LocalDateTime startAt = LocalDateTime.now().plusMinutes(10);
            Runnable job = mock(Runnable.class);
            ScheduledFuture<?> future = mock(ScheduledFuture.class);

            doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

            // when
            scheduleManager.scheduleStartJob(auctionId, startAt, job);

            // then
            assertThat(scheduleManager.hasStartJob(auctionId)).isTrue();

            ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(taskScheduler).schedule(any(Runnable.class), instantCaptor.capture());
            assertThat(instantCaptor.getValue())
                    .isEqualTo(startAt.atZone(ZoneId.systemDefault()).toInstant());
        }

        @Test
        @DisplayName("성공 - 같은 경매의 시작 잡을 다시 등록하면 기존 잡을 취소한다")
        void success_reschedule() {
            // given
            AuctionScheduleManager scheduleManager = new AuctionScheduleManager(taskScheduler);
            UUID auctionId = UUID.randomUUID();
            ScheduledFuture<?> oldFuture = mock(ScheduledFuture.class);
            ScheduledFuture<?> newFuture = mock(ScheduledFuture.class);

            doReturn(oldFuture, newFuture).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

            // when
            scheduleManager.scheduleStartJob(auctionId, LocalDateTime.now().plusMinutes(10), mock(Runnable.class));
            scheduleManager.scheduleStartJob(auctionId, LocalDateTime.now().plusMinutes(20), mock(Runnable.class));

            // then
            verify(oldFuture).cancel(false);
            assertThat(scheduleManager.hasStartJob(auctionId)).isTrue();
        }
    }

    @Nested
    @DisplayName("cancelStartJob()")
    class CancelStartJob {

        @Test
        @DisplayName("성공 - 등록된 경매 시작 잡을 취소한다")
        void success() {
            // given
            AuctionScheduleManager scheduleManager = new AuctionScheduleManager(taskScheduler);
            UUID auctionId = UUID.randomUUID();
            ScheduledFuture<?> future = mock(ScheduledFuture.class);

            doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
            doReturn(true).when(future).cancel(false);
            scheduleManager.scheduleStartJob(auctionId, LocalDateTime.now().plusMinutes(10), mock(Runnable.class));

            // when
            boolean result = scheduleManager.cancelStartJob(auctionId);

            // then
            assertThat(result).isTrue();
            assertThat(scheduleManager.hasStartJob(auctionId)).isFalse();
            verify(future).cancel(false);
        }
    }

    @Nested
    @DisplayName("scheduleEndCheckJob()")
    class ScheduleEndCheckJob {

        @Test
        @DisplayName("성공 - 경매 마감 확인 잡을 등록한다")
        void success() {
            // given
            AuctionScheduleManager scheduleManager = new AuctionScheduleManager(taskScheduler);
            UUID auctionId = UUID.randomUUID();
            ScheduledFuture<?> future = mock(ScheduledFuture.class);

            doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

            // when
            scheduleManager.scheduleEndCheckJob(auctionId, LocalDateTime.now().plusHours(1), mock(Runnable.class));

            // then
            assertThat(scheduleManager.hasEndCheckJob(auctionId)).isTrue();
        }
    }
}
