package com.bankrupang.sanjijk.auction.auction.infrastructure.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

@DisplayName("AuctionRecoveryScheduler 테스트")
@ExtendWith(MockitoExtension.class)
class AuctionRecoverySchedulerTest {

    @InjectMocks
    private AuctionRecoveryScheduler auctionRecoveryScheduler;

    @Mock
    private AuctionRepository auctionRepository;

    @Mock
    private AuctionSchedulerJobService auctionSchedulerJobService;

    @Nested
    @DisplayName("recoverStuckAuctions()")
    class RecoverStuckAuctions {

        @Test
        @DisplayName("성공 - 고착된 PROGRESS 경매들을 감지하여 마감 처리를 실행한다")
        void success() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Auction stuckAuction1 = createProgressAuction(now.minusDays(1), now.minusMinutes(10));
            Auction stuckAuction2 = createProgressAuction(now.minusDays(1), now.minusMinutes(5));

            given(auctionRepository.findAllByStatusAndEndAtBeforeAndDeletedAtIsNull(
                    any(AuctionStatus.class),
                    any(LocalDateTime.class)
            )).willReturn(List.of(stuckAuction1, stuckAuction2));

            // when
            auctionRecoveryScheduler.recoverStuckAuctions();

            // then
            verify(auctionSchedulerJobService).checkAuctionEnd(stuckAuction1.getId());
            verify(auctionSchedulerJobService).checkAuctionEnd(stuckAuction2.getId());
            verify(auctionSchedulerJobService, times(2)).checkAuctionEnd(any(UUID.class));
        }

        @Test
        @DisplayName("성공 - 고착된 경매가 없으면 마감 처리를 실행하지 않는다")
        void success_empty() {
            // given
            given(auctionRepository.findAllByStatusAndEndAtBeforeAndDeletedAtIsNull(
                    any(AuctionStatus.class),
                    any(LocalDateTime.class)
            )).willReturn(List.of());

            // when
            auctionRecoveryScheduler.recoverStuckAuctions();

            // then
            verify(auctionSchedulerJobService, never()).checkAuctionEnd(any(UUID.class));
        }

        @Test
        @DisplayName("성공 - 한 경매 복구 중 예외가 발생해도 다른 경매 복구 처리를 계속 진행한다")
        void success_exception_handled() {
            // given
            LocalDateTime now = LocalDateTime.now();
            Auction stuckAuction1 = createProgressAuction(now.minusDays(1), now.minusMinutes(10));
            Auction stuckAuction2 = createProgressAuction(now.minusDays(1), now.minusMinutes(5));

            given(auctionRepository.findAllByStatusAndEndAtBeforeAndDeletedAtIsNull(
                    any(AuctionStatus.class),
                    any(LocalDateTime.class)
            )).willReturn(List.of(stuckAuction1, stuckAuction2));

            willThrow(new RuntimeException("Temporary failure")).given(auctionSchedulerJobService).checkAuctionEnd(stuckAuction1.getId());

            // when
            auctionRecoveryScheduler.recoverStuckAuctions();

            // then
            verify(auctionSchedulerJobService).checkAuctionEnd(stuckAuction1.getId());
            verify(auctionSchedulerJobService).checkAuctionEnd(stuckAuction2.getId());
            verify(auctionSchedulerJobService, times(2)).checkAuctionEnd(any(UUID.class));
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
