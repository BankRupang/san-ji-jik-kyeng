package com.bankrupang.sanjijk.auction.auction.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.repository.AuctionRepository;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;

@DisplayName("AuctionScheduleRecovery 테스트")
@ExtendWith(MockitoExtension.class)
class AuctionScheduleRecoveryTest {

    @InjectMocks
    private AuctionScheduleRecovery auctionScheduleRecovery;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private AuctionScheduleManager auctionScheduleManager;

    @Mock
    private AuctionSchedulerJobService auctionSchedulerJobService;

    @Nested
    @DisplayName("recoverSchedules()")
    class RecoverSchedules {

        @Test
        @DisplayName("성공 - READY/PROGRESS 경매의 시작 잡과 마감 확인 잡을 복구한다")
        void success() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Auction readyFuture = createAuction(now.plusDays(1));
            Auction readyPast = createAuction(now.minusDays(1));
            Auction progressFuture = createProgressAuction(now.minusDays(1), now.plusDays(1));
            Auction progressPast = createProgressAuction(now.minusDays(1), now.minusMinutes(1));

            given(auctionRepository.findAllByStatusInAndDeletedAtIsNull(List.of(
                    AuctionStatus.READY,
                    AuctionStatus.PROGRESS
            ))).willReturn(List.of(readyFuture, readyPast, progressFuture, progressPast));

            // when
            auctionScheduleRecovery.recoverSchedules();

            // then
            verify(auctionScheduleManager).scheduleStartJob(
                    eq(readyFuture.getId()),
                    eq(readyFuture.getStartAt()),
                    any(Runnable.class)
            );
            verify(auctionSchedulerJobService, never()).startAuction(readyPast.getId());
            verify(auctionScheduleManager).scheduleEndCheckJob(
                    eq(progressFuture.getId()),
                    eq(progressFuture.getEndAt()),
                    any(Runnable.class)
            );
            verify(auctionSchedulerJobService).checkAuctionEnd(progressPast.getId());
        }

        @Test
        @DisplayName("성공 - 복구 대상 경매가 없으면 잡을 등록하지 않는다")
        void success_empty() {
            // given
            given(auctionRepository.findAllByStatusInAndDeletedAtIsNull(List.of(
                    AuctionStatus.READY,
                    AuctionStatus.PROGRESS
            ))).willReturn(List.of());

            // when
            auctionScheduleRecovery.recoverSchedules();

            // then
            verify(auctionScheduleManager, never()).scheduleStartJob(any(UUID.class), any(LocalDateTime.class), any(Runnable.class));
            verify(auctionScheduleManager, never()).scheduleEndCheckJob(any(UUID.class), any(LocalDateTime.class), any(Runnable.class));
            verify(auctionSchedulerJobService, never()).startAuction(any(UUID.class));
            verify(auctionSchedulerJobService, never()).checkAuctionEnd(any(UUID.class));
        }
    }

    private Auction createAuction(LocalDateTime startAt) {
        UUID sellerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Auction auction = Auction.create(
                productId,
                sellerId,
                10000,
                1000,
                startAt,
                startAt.plusHours(1)
        );
        ReflectionTestUtils.setField(auction, "id", UUID.randomUUID());
        return auction;
    }

    private Auction createProgressAuction(LocalDateTime startAt, LocalDateTime endAt) {
        Auction auction = createAuction(startAt);
        ReflectionTestUtils.setField(auction, "endAt", endAt);
        auction.start();
        return auction;
    }
}
