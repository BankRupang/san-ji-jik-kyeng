package com.bankrupang.sanjijk.auction.auction.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.auction.auction.exception.AuctionErrorCode;
import com.bankrupang.sanjijk.auction.auction.exception.AuctionException;

@DisplayName("Auction 엔티티 테스트")
class AuctionTest {

    @Nested
    @DisplayName("start()")
    class Start {

        @Test
        @DisplayName("성공 - READY 상태 경매를 PROGRESS 상태로 변경한다")
        void success() {
            // given
            Auction auction = createAuction(AuctionStatus.READY);

            // when
            auction.start();

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PROGRESS);
        }

        @Test
        @DisplayName("성공 - 이미 PROGRESS 상태이면 다시 처리하지 않는다")
        void success_idempotent() {
            // given
            Auction auction = createAuction(AuctionStatus.PROGRESS);

            // when & then
            assertThatCode(auction::start).doesNotThrowAnyException();
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.PROGRESS);
        }

        @Test
        @DisplayName("실패 - READY 상태가 아니면 시작할 수 없다")
        void fail_invalid_state() {
            // given
            Auction auction = createAuction(AuctionStatus.RESULT_PENDING);

            // when & then
            assertInvalidStateTransition(auction::start);
        }
    }

    @Nested
    @DisplayName("markResultPending()")
    class MarkResultPending {

        @Test
        @DisplayName("성공 - PROGRESS 상태 경매를 RESULT_PENDING 상태로 변경한다")
        void success() {
            // given
            Auction auction = createAuction(AuctionStatus.PROGRESS);

            // when
            auction.markResultPending();

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.RESULT_PENDING);
        }

        @Test
        @DisplayName("성공 - 이미 RESULT_PENDING 상태이면 다시 처리하지 않는다")
        void success_idempotent() {
            // given
            Auction auction = createAuction(AuctionStatus.RESULT_PENDING);

            // when & then
            assertThatCode(auction::markResultPending).doesNotThrowAnyException();
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.RESULT_PENDING);
        }

        @Test
        @DisplayName("실패 - PROGRESS 상태가 아니면 결과 대기 상태로 변경할 수 없다")
        void fail_invalid_state() {
            // given
            Auction auction = createAuction(AuctionStatus.READY);

            // when & then
            assertInvalidStateTransition(auction::markResultPending);
        }
    }

    @Nested
    @DisplayName("markWon()")
    class MarkWon {

        @Test
        @DisplayName("성공 - RESULT_PENDING 상태 경매를 WON 상태로 변경한다")
        void success() {
            // given
            Auction auction = createAuction(AuctionStatus.RESULT_PENDING);
            UUID winnerId = UUID.randomUUID();
            Integer finalPrice = 20000;

            // when
            auction.markWon(winnerId, finalPrice);

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.WON);
            assertThat(auction.getWinnerId()).isEqualTo(winnerId);
            assertThat(auction.getFinalPrice()).isEqualTo(finalPrice);
        }

        @Test
        @DisplayName("성공 - 최종 낙찰가가 시작가와 같으면 낙찰 처리한다")
        void success_same_as_start_price() {
            // given
            Auction auction = createAuction(AuctionStatus.RESULT_PENDING);
            UUID winnerId = UUID.randomUUID();
            Integer finalPrice = 10000;

            // when
            auction.markWon(winnerId, finalPrice);

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.WON);
            assertThat(auction.getWinnerId()).isEqualTo(winnerId);
            assertThat(auction.getFinalPrice()).isEqualTo(finalPrice);
        }

        @Test
        @DisplayName("성공 - 이미 WON 상태이면 다시 처리하지 않는다")
        void success_idempotent() {
            // given
            UUID winnerId = UUID.randomUUID();
            Integer finalPrice = 20000;
            Auction auction = createAuction(AuctionStatus.WON);
            ReflectionTestUtils.setField(auction, "winnerId", winnerId);
            ReflectionTestUtils.setField(auction, "finalPrice", finalPrice);

            // when & then
            assertThatCode(() -> auction.markWon(UUID.randomUUID(), 30000)).doesNotThrowAnyException();
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.WON);
            assertThat(auction.getWinnerId()).isEqualTo(winnerId);
            assertThat(auction.getFinalPrice()).isEqualTo(finalPrice);
        }

        @Test
        @DisplayName("실패 - RESULT_PENDING 상태가 아니면 낙찰 처리할 수 없다")
        void fail_invalid_state() {
            // given
            Auction auction = createAuction(AuctionStatus.PROGRESS);

            // when & then
            assertInvalidStateTransition(() -> auction.markWon(UUID.randomUUID(), 20000));
        }

        @Test
        @DisplayName("실패 - 낙찰자가 없으면 낙찰 처리할 수 없다")
        void fail_winner_id_null() {
            // given
            Auction auction = createAuction(AuctionStatus.RESULT_PENDING);

            // when & then
            assertInvalidAuctionResult(() -> auction.markWon(null, 20000));
        }

        @Test
        @DisplayName("실패 - 최종 낙찰가가 없으면 낙찰 처리할 수 없다")
        void fail_final_price_null() {
            // given
            Auction auction = createAuction(AuctionStatus.RESULT_PENDING);

            // when & then
            assertInvalidAuctionResult(() -> auction.markWon(UUID.randomUUID(), null));
        }

        @Test
        @DisplayName("실패 - 최종 낙찰가가 0 이하이면 낙찰 처리할 수 없다")
        void fail_final_price_not_positive() {
            // given
            Auction auction = createAuction(AuctionStatus.RESULT_PENDING);

            // when & then
            assertInvalidAuctionResult(() -> auction.markWon(UUID.randomUUID(), 0));
        }

        @Test
        @DisplayName("실패 - 시작가가 없으면 낙찰 처리할 수 없다")
        void fail_start_price_null() {
            // given
            Auction auction = createAuction(AuctionStatus.RESULT_PENDING);
            ReflectionTestUtils.setField(auction, "startPrice", null);

            // when & then
            assertInvalidAuctionResult(() -> auction.markWon(UUID.randomUUID(), 20000));
        }

        @Test
        @DisplayName("실패 - 최종 낙찰가가 시작가보다 낮으면 낙찰 처리할 수 없다")
        void fail_final_price_less_than_start_price() {
            // given
            Auction auction = createAuction(AuctionStatus.RESULT_PENDING);

            // when & then
            assertInvalidAuctionResult(() -> auction.markWon(UUID.randomUUID(), 9999));
        }
    }

    @Nested
    @DisplayName("markSuccess()")
    class MarkSuccess {

        @Test
        @DisplayName("성공 - WON 상태 경매를 SUCCESS 상태로 변경한다")
        void success() {
            // given
            Auction auction = createAuction(AuctionStatus.WON);

            // when
            auction.markSuccess();

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.SUCCESS);
        }

        @Test
        @DisplayName("성공 - 이미 SUCCESS 상태이면 다시 처리하지 않는다")
        void success_idempotent() {
            // given
            Auction auction = createAuction(AuctionStatus.SUCCESS);

            // when & then
            assertThatCode(auction::markSuccess).doesNotThrowAnyException();
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.SUCCESS);
        }

        @Test
        @DisplayName("실패 - WON 상태가 아니면 성공 처리할 수 없다")
        void fail_invalid_state() {
            // given
            Auction auction = createAuction(AuctionStatus.RESULT_PENDING);

            // when & then
            assertInvalidStateTransition(auction::markSuccess);
        }
    }

    @Nested
    @DisplayName("markFailed()")
    class MarkFailed {

        @Test
        @DisplayName("성공 - RESULT_PENDING 상태 경매를 FAIL 상태로 변경한다")
        void success_result_pending() {
            // given
            Auction auction = createAuction(AuctionStatus.RESULT_PENDING);

            // when
            auction.markFailed();

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAIL);
        }

        @Test
        @DisplayName("성공 - WON 상태 경매를 FAIL 상태로 변경한다")
        void success_won() {
            // given
            Auction auction = createAuction(AuctionStatus.WON);

            // when
            auction.markFailed();

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAIL);
        }

        @Test
        @DisplayName("성공 - 이미 FAIL 상태이면 다시 처리하지 않는다")
        void success_idempotent() {
            // given
            Auction auction = createAuction(AuctionStatus.FAIL);

            // when & then
            assertThatCode(auction::markFailed).doesNotThrowAnyException();
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAIL);
        }

        @Test
        @DisplayName("실패 - RESULT_PENDING 또는 WON 상태가 아니면 실패 처리할 수 없다")
        void fail_invalid_state() {
            // given
            Auction auction = createAuction(AuctionStatus.READY);

            // when & then
            assertInvalidStateTransition(auction::markFailed);
        }
    }

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("성공 - READY 상태 경매를 CANCELLED 상태로 변경한다")
        void success_ready() {
            // given
            Auction auction = createAuction(AuctionStatus.READY);

            // when
            auction.cancel();

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        }

        @Test
        @DisplayName("성공 - PROGRESS 상태 경매를 CANCELLED 상태로 변경한다")
        void success_progress() {
            // given
            Auction auction = createAuction(AuctionStatus.PROGRESS);

            // when
            auction.cancel();

            // then
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        }

        @Test
        @DisplayName("성공 - 이미 CANCELLED 상태이면 다시 처리하지 않는다")
        void success_idempotent() {
            // given
            Auction auction = createAuction(AuctionStatus.CANCELLED);

            // when & then
            assertThatCode(auction::cancel).doesNotThrowAnyException();
            assertThat(auction.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        }

        @Test
        @DisplayName("실패 - READY 또는 PROGRESS 상태가 아니면 취소할 수 없다")
        void fail_invalid_state() {
            // given
            Auction auction = createAuction(AuctionStatus.WON);

            // when & then
            assertInvalidStateTransition(auction::cancel);
        }
    }

    @Nested
    @DisplayName("validateEditable()")
    class ValidateEditable {

        @Test
        @DisplayName("성공 - READY 상태 경매는 수정 가능하다")
        void success() {
            // given
            Auction auction = createAuction(AuctionStatus.READY);

            // when & then
            assertThatCode(auction::validateEditable).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("실패 - READY 상태가 아니면 수정할 수 없다")
        void fail_not_editable() {
            // given
            Auction auction = createAuction(AuctionStatus.PROGRESS);

            // when & then
            assertThatThrownBy(auction::validateEditable)
                    .isInstanceOf(AuctionException.class)
                    .hasMessage(AuctionErrorCode.AUCTION_NOT_EDITABLE.getMessage());
        }
    }

    private Auction createAuction(AuctionStatus status) {
        LocalDateTime startAt = LocalDateTime.now().plusDays(1);
        Auction auction = Auction.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                10000,
                1000,
                startAt,
                startAt.plusHours(1)
        );
        ReflectionTestUtils.setField(auction, "status", status);
        return auction;
    }

    private void assertInvalidStateTransition(Runnable runnable) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(AuctionException.class)
                .hasMessage(AuctionErrorCode.INVALID_STATE_TRANSITION.getMessage());
    }

    private void assertInvalidAuctionResult(Runnable runnable) {
        assertThatThrownBy(runnable::run)
                .isInstanceOf(AuctionException.class)
                .hasMessage(AuctionErrorCode.INVALID_AUCTION_RESULT.getMessage());
    }
}
