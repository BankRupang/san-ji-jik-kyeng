package com.bankrupang.sanjijk.notification.application.service;

import com.bankrupang.sanjijk.notification.domain.entity.NotificationLog;
import com.bankrupang.sanjijk.notification.domain.repository.NotificationLogRepository;
import com.bankrupang.sanjijk.notification.infrastructure.feign.UserNotificationResponse;
import com.bankrupang.sanjijk.notification.infrastructure.messaging.dto.AuctionFailedEvent;
import com.bankrupang.sanjijk.notification.infrastructure.messaging.dto.AuctionWonEvent;
import com.bankrupang.sanjijk.notification.infrastructure.messaging.dto.BidOvertakenEvent;
import com.bankrupang.sanjijk.notification.infrastructure.messaging.dto.DepositForfeitedEvent;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

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
        ReflectionTestUtils.setField(event, "occurredAt", LocalDateTime.now());
        return event;
    }

    private BidOvertakenEvent createBidOvertakenEvent() {
        BidOvertakenEvent event = new BidOvertakenEvent();
        ReflectionTestUtils.setField(event, "auctionId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "auctionTitle", "테스트 경매");
        ReflectionTestUtils.setField(event, "previousBidderId", UUID.randomUUID());
        ReflectionTestUtils.setField(event, "newPrice", 1_100_000);
        ReflectionTestUtils.setField(event, "nextMinPrice", 1_200_000);
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
