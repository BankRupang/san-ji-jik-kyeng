package com.bankrupang.sanjijk.notification.application.service;

import com.bankrupang.sanjijk.notification.domain.entity.NotificationLog;
import com.bankrupang.sanjijk.notification.domain.repository.NotificationLogRepository;
import com.bankrupang.sanjijk.notification.infrastructure.feign.UserNotificationResponse;
import com.bankrupang.sanjijk.notification.infrastructure.messaging.dto.AuctionFailedEvent;
import com.bankrupang.sanjijk.notification.infrastructure.messaging.dto.AuctionWonEvent;
import com.bankrupang.sanjijk.notification.infrastructure.messaging.dto.BidOvertakenEvent;
import com.bankrupang.sanjijk.notification.infrastructure.messaging.dto.DepositForfeitedEvent;
import com.bankrupang.sanjijk.notification.infrastructure.messaging.dto.RefundCompletedEvent;
import com.bankrupang.sanjijk.notification.infrastructure.messaging.dto.PaymentCompletedEvent;
import com.bankrupang.sanjijk.notification.infrastructure.messaging.dto.PaymentFailedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

import com.bankrupang.sanjijk.notification.domain.enums.NotificationStatus;
import com.bankrupang.sanjijk.notification.domain.enums.NotificationType;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventService 단위 테스트")
class NotificationEventServiceTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private UserNotificationCacheService userNotificationCacheService;

    @InjectMocks
    private NotificationEventService notificationEventService;

    @Nested
    @DisplayName("resolveReferenceType()")
    class ResolveReferenceType {

        @Test
        @DisplayName("경매 이벤트(AUCTION_WON) → referenceType = AUCTION")
        void auctionEvent() {
            // given
            AuctionWonEvent event = createAuctionWonEvent();
            given(userNotificationCacheService.getNotificationEnabled(any()))
                    .willReturn(new UserNotificationResponse(UUID.randomUUID(), "U_SLACK", true));

            // when
            notificationEventService.handleAuctionWon(event);

            // then
            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            then(notificationLogRepository).should(times(2)).save(captor.capture());
            assertThat(captor.getAllValues())
                    .allMatch(log -> "AUCTION".equals(log.getReferenceType()));
        }

        @Test
        @DisplayName("결제 이벤트(PAYMENT_COMPLETED) → referenceType = PAYMENT")
        void paymentEvent() {
            // given
            PaymentCompletedEvent event = createPaymentCompletedEvent();
            given(userNotificationCacheService.getNotificationEnabled(any()))
                    .willReturn(new UserNotificationResponse(UUID.randomUUID(), "U_SLACK", true));

            // when
            notificationEventService.handlePaymentCompleted(event);

            // then
            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            then(notificationLogRepository).should(times(2)).save(captor.capture());
            assertThat(captor.getAllValues())
                    .allMatch(log -> "PAYMENT".equals(log.getReferenceType()));
        }

        @Test
        @DisplayName("입찰 이벤트(BID_OVERTAKEN) → referenceType = BID")
        void bidEvent() {
            // given
            BidOvertakenEvent event = createBidOvertakenEvent();
            given(userNotificationCacheService.getNotificationEnabled(any()))
                    .willReturn(new UserNotificationResponse(UUID.randomUUID(), "U_SLACK", true));

            // when
            notificationEventService.handleBidOvertaken(event);

            // then
            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            then(notificationLogRepository).should(times(1)).save(captor.capture());
            assertThat(captor.getValue().getReferenceType()).isEqualTo("BID");
        }

        @Test
        @DisplayName("경매 실패 이벤트(AUCTION_FAILED) → referenceType = AUCTION")
        void auctionFailedEvent() {
            // given
            AuctionFailedEvent event = createAuctionFailedEvent();
            given(userNotificationCacheService.getNotificationEnabled(any()))
                    .willReturn(new UserNotificationResponse(UUID.randomUUID(), "U_SLACK", true));

            // when
            notificationEventService.handleAuctionFailed(event);

            // then
            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            then(notificationLogRepository).should(times(1)).save(captor.capture());
            assertThat(captor.getValue().getReferenceType()).isEqualTo("AUCTION");
        }

        @Test
        @DisplayName("결제 실패 이벤트(PAYMENT_FAILED) → referenceType = PAYMENT")
        void paymentFailedEvent() {
            // given
            PaymentFailedEvent event = createPaymentFailedEvent();
            given(userNotificationCacheService.getNotificationEnabled(any()))
                    .willReturn(new UserNotificationResponse(UUID.randomUUID(), "U_SLACK", true));

            // when
            notificationEventService.handlePaymentFailed(event);

            // then
            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            then(notificationLogRepository).should(times(1)).save(captor.capture());
            assertThat(captor.getValue().getReferenceType()).isEqualTo("PAYMENT");
        }

        @Test
        @DisplayName("환불 완료 이벤트(REFUND_COMPLETED) → referenceType = PAYMENT")
        void refundCompletedEvent() {
            // given
            RefundCompletedEvent event = createRefundCompletedEvent();
            given(userNotificationCacheService.getNotificationEnabled(any()))
                    .willReturn(new UserNotificationResponse(UUID.randomUUID(), "U_SLACK", true));

            // when
            notificationEventService.handleRefundCompleted(event);

            // then
            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            then(notificationLogRepository).should(times(1)).save(captor.capture());
            assertThat(captor.getValue().getReferenceType()).isEqualTo("PAYMENT");
        }

        @Test
        @DisplayName("예치금 몰수 이벤트(DEPOSIT_FORFEITED) → referenceType = PAYMENT")
        void depositForfeitedEvent() {
            // given
            DepositForfeitedEvent event = createDepositForfeitedEvent();
            given(userNotificationCacheService.getNotificationEnabled(any()))
                    .willReturn(new UserNotificationResponse(UUID.randomUUID(), "U_SLACK", true));

            // when
            notificationEventService.handleDepositForfeited(event);

            // then
            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            then(notificationLogRepository).should(times(2)).save(captor.capture());
            assertThat(captor.getAllValues())
                    .allMatch(log -> "PAYMENT".equals(log.getReferenceType()));
        }
    }

    @Nested
    @DisplayName("handleBidOvertaken() — 이전 입찰자 없음")
    class BidOvertakenNoPreviousBidder {

        @Test
        @DisplayName("previousBidderId가 null이면 저장 및 이벤트 발행 안 함 (첫 입찰)")
        void nullPreviousBidderId() {
            // given
            BidOvertakenEvent event = new BidOvertakenEvent();
            ReflectionTestUtils.setField(event, "auctionId", UUID.randomUUID().toString());
            ReflectionTestUtils.setField(event, "auctionTitle", "테스트 경매");
            ReflectionTestUtils.setField(event, "previousBidderId", null);
            ReflectionTestUtils.setField(event, "newPrice", 1_100_000L);
            ReflectionTestUtils.setField(event, "nextMinPrice", 1_200_000L);

            // when
            notificationEventService.handleBidOvertaken(event);

            // then
            then(notificationLogRepository).shouldHaveNoInteractions();
            then(eventPublisher).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("previousBidderId가 빈 문자열이면 저장 및 이벤트 발행 안 함 (첫 입찰)")
        void emptyPreviousBidderId() {
            // given
            BidOvertakenEvent event = new BidOvertakenEvent();
            ReflectionTestUtils.setField(event, "auctionId", UUID.randomUUID().toString());
            ReflectionTestUtils.setField(event, "auctionTitle", "테스트 경매");
            ReflectionTestUtils.setField(event, "previousBidderId", "");
            ReflectionTestUtils.setField(event, "newPrice", 1_100_000L);
            ReflectionTestUtils.setField(event, "nextMinPrice", 1_200_000L);

            // when
            notificationEventService.handleBidOvertaken(event);

            // then
            then(notificationLogRepository).shouldHaveNoInteractions();
            then(eventPublisher).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("processAndPublish() — 알림 수신 거부")
    class NotificationAllow {

        @Test
        @DisplayName("notificationAllow=false 이면 저장 및 이벤트 발행 안 함")
        void notAllowed() {
            // given
            AuctionWonEvent event = createAuctionWonEvent();
            given(userNotificationCacheService.getNotificationEnabled(any()))
                    .willReturn(new UserNotificationResponse(UUID.randomUUID(), "U_SLACK", false));

            // when
            notificationEventService.handleAuctionWon(event);

            // then
            then(notificationLogRepository).shouldHaveNoInteractions();
            then(eventPublisher).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("processAndPublish() — slackId 검증")
    class SlackIdValidation {

        @Test
        @DisplayName("slackId가 null이면 저장 및 이벤트 발행 안 함")
        void nullSlackId() {
            // given
            AuctionWonEvent event = createAuctionWonEvent();
            given(userNotificationCacheService.getNotificationEnabled(any()))
                    .willReturn(new UserNotificationResponse(UUID.randomUUID(), null, true));

            // when
            notificationEventService.handleAuctionWon(event);

            // then
            then(notificationLogRepository).shouldHaveNoInteractions();
            then(eventPublisher).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("slackId가 빈 문자열이면 저장 및 이벤트 발행 안 함")
        void blankSlackId() {
            // given
            AuctionWonEvent event = createAuctionWonEvent();
            given(userNotificationCacheService.getNotificationEnabled(any()))
                    .willReturn(new UserNotificationResponse(UUID.randomUUID(), "", true));

            // when
            notificationEventService.handleAuctionWon(event);

            // then
            then(notificationLogRepository).shouldHaveNoInteractions();
            then(eventPublisher).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("알림 메시지 내용 검증")
    class MessageContent {

        @Test
        @DisplayName("낙찰 확정 - 낙찰자: 결제 기한(occurredAt+15분) 포함, 판매자: 미포함")
        void auctionWon_messages() {
            // given
            LocalDateTime occurredAt = LocalDateTime.of(2024, 6, 23, 10, 30);
            AuctionWonEvent event = new AuctionWonEvent();
            ReflectionTestUtils.setField(event, "auctionId", UUID.randomUUID());
            ReflectionTestUtils.setField(event, "auctionTitle", "테스트 경매");
            ReflectionTestUtils.setField(event, "winnerId", UUID.randomUUID());
            ReflectionTestUtils.setField(event, "sellerId", UUID.randomUUID());
            ReflectionTestUtils.setField(event, "finalPrice", 1_000_000);
            ReflectionTestUtils.setField(event, "occurredAt", occurredAt);
            given(userNotificationCacheService.getNotificationEnabled(any()))
                    .willReturn(new UserNotificationResponse(UUID.randomUUID(), "U_SLACK", true));

            // when
            notificationEventService.handleAuctionWon(event);

            // then
            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            then(notificationLogRepository).should(times(2)).save(captor.capture());
            List<NotificationLog> logs = captor.getAllValues();

            assertThat(logs.get(0).getMessage())
                    .contains("테스트 경매")
                    .contains("1,000,000원")
                    .contains("06월 23일 10:45");
            assertThat(logs.get(1).getMessage())
                    .contains("테스트 경매")
                    .contains("1,000,000원")
                    .doesNotContain("결제 기한");
        }

        @Test
        @DisplayName("결제 완료 - 낙찰자: paidAmount 사용, 판매자: finalPrice 사용")
        void paymentCompleted_messages() {
            // given
            PaymentCompletedEvent event = new PaymentCompletedEvent();
            ReflectionTestUtils.setField(event, "auctionId", UUID.randomUUID());
            ReflectionTestUtils.setField(event, "auctionTitle", "테스트 경매");
            ReflectionTestUtils.setField(event, "winnerId", UUID.randomUUID());
            ReflectionTestUtils.setField(event, "sellerId", UUID.randomUUID());
            ReflectionTestUtils.setField(event, "finalPrice", 1_000_000);
            ReflectionTestUtils.setField(event, "paidAmount", 950_000);
            ReflectionTestUtils.setField(event, "paymentType", "NORMAL");
            ReflectionTestUtils.setField(event, "occurredAt", LocalDateTime.now());
            given(userNotificationCacheService.getNotificationEnabled(any()))
                    .willReturn(new UserNotificationResponse(UUID.randomUUID(), "U_SLACK", true));

            // when
            notificationEventService.handlePaymentCompleted(event);

            // then
            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            then(notificationLogRepository).should(times(2)).save(captor.capture());
            List<NotificationLog> logs = captor.getAllValues();

            assertThat(logs.get(0).getMessage())
                    .contains("결제 완료!")
                    .contains("950,000원");
            assertThat(logs.get(1).getMessage())
                    .contains("거래 완료!")
                    .contains("1,000,000원");
        }

        @Test
        @DisplayName("결제 실패 - 실패 사유 포함")
        void paymentFailed_message() {
            // given
            PaymentFailedEvent event = new PaymentFailedEvent();
            ReflectionTestUtils.setField(event, "auctionId", UUID.randomUUID());
            ReflectionTestUtils.setField(event, "auctionTitle", "테스트 경매");
            ReflectionTestUtils.setField(event, "winnerId", UUID.randomUUID());
            ReflectionTestUtils.setField(event, "failureMessage", "잔액 부족");
            ReflectionTestUtils.setField(event, "occurredAt", LocalDateTime.now());
            given(userNotificationCacheService.getNotificationEnabled(any()))
                    .willReturn(new UserNotificationResponse(UUID.randomUUID(), "U_SLACK", true));

            // when
            notificationEventService.handlePaymentFailed(event);

            // then
            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            then(notificationLogRepository).should(times(1)).save(captor.capture());
            assertThat(captor.getValue().getMessage())
                    .contains("잔액 부족")
                    .contains("15분 내 재결제");
        }

        @Test
        @DisplayName("예치금 몰수 - 낙찰자: '예치금 몰수 안내', 판매자: '보상금 정산 안내'")
        void depositForfeited_messages() {
            // given
            DepositForfeitedEvent event = new DepositForfeitedEvent();
            ReflectionTestUtils.setField(event, "auctionId", UUID.randomUUID());
            ReflectionTestUtils.setField(event, "auctionTitle", "테스트 경매");
            ReflectionTestUtils.setField(event, "winnerId", UUID.randomUUID());
            ReflectionTestUtils.setField(event, "sellerId", UUID.randomUUID());
            ReflectionTestUtils.setField(event, "forfeitedAmount", 500_000);
            ReflectionTestUtils.setField(event, "occurredAt", LocalDateTime.now());
            given(userNotificationCacheService.getNotificationEnabled(any()))
                    .willReturn(new UserNotificationResponse(UUID.randomUUID(), "U_SLACK", true));

            // when
            notificationEventService.handleDepositForfeited(event);

            // then
            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            then(notificationLogRepository).should(times(2)).save(captor.capture());
            List<NotificationLog> logs = captor.getAllValues();

            assertThat(logs.get(0).getMessage())
                    .startsWith("예치금 몰수 안내")
                    .contains("500,000원");
            assertThat(logs.get(1).getMessage())
                    .startsWith("보상금 정산 안내")
                    .contains("500,000원");
        }

        @Test
        @DisplayName("입찰 추월 - 현재가 및 다음 최소 입찰가 포함")
        void bidOvertaken_message() {
            // given
            BidOvertakenEvent event = new BidOvertakenEvent();
            ReflectionTestUtils.setField(event, "auctionId", UUID.randomUUID().toString());
            ReflectionTestUtils.setField(event, "auctionTitle", "테스트 경매");
            ReflectionTestUtils.setField(event, "previousBidderId", UUID.randomUUID().toString());
            ReflectionTestUtils.setField(event, "newPrice", 1_100_000L);
            ReflectionTestUtils.setField(event, "nextMinPrice", 1_200_000L);
            given(userNotificationCacheService.getNotificationEnabled(any()))
                    .willReturn(new UserNotificationResponse(UUID.randomUUID(), "U_SLACK", true));

            // when
            notificationEventService.handleBidOvertaken(event);

            // then
            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            then(notificationLogRepository).should(times(1)).save(captor.capture());
            assertThat(captor.getValue().getMessage())
                    .contains("1,100,000원")
                    .contains("1,200,000원");
        }

        @Test
        @DisplayName("환불 완료 - 환불 금액 포함")
        void refundCompleted_message() {
            // given
            RefundCompletedEvent event = new RefundCompletedEvent();
            ReflectionTestUtils.setField(event, "auctionId", UUID.randomUUID());
            ReflectionTestUtils.setField(event, "auctionTitle", "테스트 경매");
            ReflectionTestUtils.setField(event, "userId", UUID.randomUUID());
            ReflectionTestUtils.setField(event, "cancelAmount", 500_000);
            ReflectionTestUtils.setField(event, "occurredAt", LocalDateTime.now());
            given(userNotificationCacheService.getNotificationEnabled(any()))
                    .willReturn(new UserNotificationResponse(UUID.randomUUID(), "U_SLACK", true));

            // when
            notificationEventService.handleRefundCompleted(event);

            // then
            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            then(notificationLogRepository).should(times(1)).save(captor.capture());
            assertThat(captor.getValue().getMessage())
                    .contains("보증금 환불 완료")
                    .contains("500,000원");
        }
    }

    @Nested
    @DisplayName("markSent() / scheduleRetry()")
    class MarkSentAndScheduleRetry {

        @Test
        @DisplayName("markSent: 로그가 존재하면 SENT 상태로 전환")
        void markSent_found() {
            // given
            UUID logId = UUID.randomUUID();
            NotificationLog log = NotificationLog.create(
                    UUID.randomUUID(), NotificationType.AUCTION_WON,
                    "낙찰 확정!", "메시지", UUID.randomUUID(), "AUCTION", "U_SLACK");
            given(notificationLogRepository.findById(logId)).willReturn(Optional.of(log));

            // when
            notificationEventService.markSent(logId);

            // then
            assertThat(log.getStatus()).isEqualTo(NotificationStatus.SENT);
        }

        @Test
        @DisplayName("markSent: 로그가 없으면 예외 없이 경고만 출력")
        void markSent_notFound() {
            // given
            UUID logId = UUID.randomUUID();
            given(notificationLogRepository.findById(logId)).willReturn(Optional.empty());

            // when & then
            assertThatCode(() -> notificationEventService.markSent(logId))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("scheduleRetry: 로그가 존재하면 retryCount 증가 및 PENDING 유지")
        void scheduleRetry_found() {
            // given
            UUID logId = UUID.randomUUID();
            NotificationLog log = NotificationLog.create(
                    UUID.randomUUID(), NotificationType.AUCTION_WON,
                    "낙찰 확정!", "메시지", UUID.randomUUID(), "AUCTION", "U_SLACK");
            given(notificationLogRepository.findById(logId)).willReturn(Optional.of(log));

            // when
            notificationEventService.scheduleRetry(logId);

            // then
            assertThat(log.getRetryCount()).isEqualTo(1);
            assertThat(log.getStatus()).isEqualTo(NotificationStatus.PENDING);
        }

        @Test
        @DisplayName("scheduleRetry: 로그가 없으면 예외 없이 경고만 출력")
        void scheduleRetry_notFound() {
            // given
            UUID logId = UUID.randomUUID();
            given(notificationLogRepository.findById(logId)).willReturn(Optional.empty());

            // when & then
            assertThatCode(() -> notificationEventService.scheduleRetry(logId))
                    .doesNotThrowAnyException();
        }
    }

    private AuctionWonEvent createAuctionWonEvent() {
        AuctionWonEvent event = new AuctionWonEvent();
        ReflectionTestUtils.setField(event, "auctionId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "auctionTitle", "테스트 경매");
        ReflectionTestUtils.setField(event, "winnerId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "sellerId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "finalPrice", 1_000_000);
        ReflectionTestUtils.setField(event, "occurredAt", LocalDateTime.now());
        return event;
    }

    private PaymentCompletedEvent createPaymentCompletedEvent() {
        PaymentCompletedEvent event = new PaymentCompletedEvent();
        ReflectionTestUtils.setField(event, "auctionId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "auctionTitle", "테스트 경매");
        ReflectionTestUtils.setField(event, "winnerId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "sellerId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "finalPrice", 1_000_000);
        ReflectionTestUtils.setField(event, "paidAmount", 1_000_000);
        ReflectionTestUtils.setField(event, "paymentType", "NORMAL");
        ReflectionTestUtils.setField(event, "occurredAt", LocalDateTime.now());
        return event;
    }

    private BidOvertakenEvent createBidOvertakenEvent() {
        BidOvertakenEvent event = new BidOvertakenEvent();
        ReflectionTestUtils.setField(event, "auctionId", UUID.randomUUID().toString());
        ReflectionTestUtils.setField(event, "auctionTitle", "테스트 경매");
        ReflectionTestUtils.setField(event, "previousBidderId", UUID.randomUUID().toString());
        ReflectionTestUtils.setField(event, "newPrice", 1_100_000L);
        ReflectionTestUtils.setField(event, "nextMinPrice", 1_200_000L);
        return event;
    }

    private AuctionFailedEvent createAuctionFailedEvent() {
        AuctionFailedEvent event = new AuctionFailedEvent();
        ReflectionTestUtils.setField(event, "auctionId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "auctionTitle", "테스트 경매");
        ReflectionTestUtils.setField(event, "sellerId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "occurredAt", LocalDateTime.now());
        return event;
    }

    private PaymentFailedEvent createPaymentFailedEvent() {
        PaymentFailedEvent event = new PaymentFailedEvent();
        ReflectionTestUtils.setField(event, "auctionId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "auctionTitle", "테스트 경매");
        ReflectionTestUtils.setField(event, "winnerId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "failureMessage", "잔액 부족");
        ReflectionTestUtils.setField(event, "occurredAt", LocalDateTime.now());
        return event;
    }

    private RefundCompletedEvent createRefundCompletedEvent() {
        RefundCompletedEvent event = new RefundCompletedEvent();
        ReflectionTestUtils.setField(event, "auctionId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "auctionTitle", "테스트 경매");
        ReflectionTestUtils.setField(event, "userId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "cancelAmount", 500_000);
        ReflectionTestUtils.setField(event, "occurredAt", LocalDateTime.now());
        return event;
    }

    private DepositForfeitedEvent createDepositForfeitedEvent() {
        DepositForfeitedEvent event = new DepositForfeitedEvent();
        ReflectionTestUtils.setField(event, "auctionId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "auctionTitle", "테스트 경매");
        ReflectionTestUtils.setField(event, "winnerId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "sellerId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "forfeitedAmount", 500_000);
        ReflectionTestUtils.setField(event, "occurredAt", LocalDateTime.now());
        return event;
    }
}
