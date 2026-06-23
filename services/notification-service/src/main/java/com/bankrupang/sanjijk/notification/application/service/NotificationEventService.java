package com.bankrupang.sanjijk.notification.application.service;

import com.bankrupang.sanjijk.notification.application.event.SlackNotificationEvent;
import com.bankrupang.sanjijk.notification.domain.entity.NotificationLog;
import com.bankrupang.sanjijk.notification.domain.enums.NotificationType;
import com.bankrupang.sanjijk.notification.domain.repository.NotificationLogRepository;
import com.bankrupang.sanjijk.notification.infrastructure.feign.UserNotificationResponse;
import com.bankrupang.sanjijk.notification.infrastructure.messaging.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationEventService {

    private static final DateTimeFormatter DUE_AT_FORMATTER =
            DateTimeFormatter.ofPattern("MM월 dd일 HH:mm");

    private final NotificationLogRepository notificationLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final UserNotificationCacheService userNotificationCacheService;

    public void handleAuctionWon(AuctionWonEvent event) {
        String dueAt = event.getOccurredAt().plusMinutes(15).format(DUE_AT_FORMATTER);

        processAndPublish(event.getWinnerId(), NotificationType.AUCTION_WON,
                "낙찰 확정!",
                String.format("낙찰 확정! %s / 낙찰가: %,d원 / 결제 기한: %s",
                        event.getAuctionTitle(), event.getFinalPrice(), dueAt),
                event.getAuctionId());

        processAndPublish(event.getSellerId(), NotificationType.AUCTION_WON,
                "낙찰 확정!",
                String.format("낙찰 확정! %s / 낙찰가: %,d원",
                        event.getAuctionTitle(), event.getFinalPrice()),
                event.getAuctionId());
    }

    public void handleAuctionFailed(AuctionFailedEvent event) {
        processAndPublish(event.getSellerId(), NotificationType.AUCTION_FAILED,
                "유찰 안내",
                String.format("유찰 안내 / %s / 입찰자가 없어 유찰되었습니다. 새 경매방을 직접 생성해주세요.",
                        event.getAuctionTitle()),
                event.getAuctionId());
    }

    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        processAndPublish(event.getWinnerId(), NotificationType.PAYMENT_COMPLETED,
                "결제 완료!",
                String.format("결제 완료! %s / 결제 금액: %,d원",
                        event.getAuctionTitle(), event.getPaidAmount()),
                event.getAuctionId());

        // REPAY(보증금)는 sellerId = null, finalPrice = null → 판매자 알림 스킵
        if (!"REPAY".equals(event.getPaymentType())) {
            processAndPublish(event.getSellerId(), NotificationType.PAYMENT_COMPLETED,
                    "거래 완료!",
                    String.format("거래 완료! %s / 낙찰가: %,d원",
                            event.getAuctionTitle(), event.getFinalPrice()),
                    event.getAuctionId());
        }
    }

    public void handlePaymentFailed(PaymentFailedEvent event) {
        processAndPublish(event.getWinnerId(), NotificationType.PAYMENT_FAILED,
                "결제 실패",
                String.format("결제 실패 / %s / 사유: %s / 15분 내 재결제를 시도해주세요.",
                        event.getAuctionTitle(), event.getFailureMessage()),
                event.getAuctionId());
    }

    public void handleRefundCompleted(RefundCompletedEvent event) {
        processAndPublish(event.getUserId(), NotificationType.REFUND_COMPLETED,
                "보증금 환불 완료",
                String.format("보증금 환불 완료 / %s / 환불 금액: %,d원",
                        event.getAuctionTitle(), event.getCancelAmount()),
                event.getAuctionId());
    }

    public void handleDepositForfeited(DepositForfeitedEvent event) {
        processAndPublish(event.getWinnerId(), NotificationType.DEPOSIT_FORFEITED,
                "예치금 몰수 안내",
                String.format("예치금 몰수 안내 / %s / 몰수 금액: %,d원",
                        event.getAuctionTitle(), event.getForfeitedAmount()),
                event.getAuctionId());

        processAndPublish(event.getSellerId(), NotificationType.DEPOSIT_FORFEITED,
                "보상금 정산 안내",
                String.format("보상금 정산 안내 / %s / 보상금: %,d원",
                        event.getAuctionTitle(), event.getForfeitedAmount()),
                event.getAuctionId());
    }

    public void handleBidOvertaken(BidOvertakenEvent event) {
        if (event.getPreviousBidderId() == null || event.getPreviousBidderId().isBlank()) {
            log.info("이전 최고 입찰자 없음 (첫 입찰), BID_OVERTAKEN 알림 스킵");
            return;
        }
        if (event.getNewPrice() == null || event.getNextMinPrice() == null) {
            log.warn("BID_OVERTAKEN 가격 정보 누락, 알림 스킵 - auctionId={}", event.getAuctionId());
            return;
        }
        processAndPublish(UUID.fromString(event.getPreviousBidderId()), NotificationType.BID_OVERTAKEN,
                "입찰 추월 알림",
                String.format("입찰 추월 알림 / %s / 현재가: %,d원 / 다음 최소 입찰가: %,d원",
                        event.getAuctionTitle(), event.getNewPrice(), event.getNextMinPrice()),
                UUID.fromString(event.getAuctionId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(UUID logId) {
        notificationLogRepository.findById(logId)
                .ifPresentOrElse(
                        NotificationLog::markSent,
                        () -> log.warn("markSent: NotificationLog not found, logId={}", logId)
                );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleRetry(UUID logId) {
        notificationLogRepository.findById(logId)
                .ifPresentOrElse(
                        NotificationLog::scheduleRetry,
                        () -> log.warn("scheduleRetry: NotificationLog not found, logId={}", logId)
                );
    }

    private String resolveReferenceType(NotificationType type) {
        return switch (type) {
            case PAYMENT_COMPLETED, PAYMENT_FAILED, DEPOSIT_FORFEITED, REFUND_COMPLETED -> "PAYMENT";
            case BID_OVERTAKEN -> "BID";
            default -> "AUCTION";
        };
    }

    private void processAndPublish(UUID userId, NotificationType type,
                                   String title, String message, UUID referenceId) {
        if (userId == null) {
            log.warn("userId is null, 알림 스킵 - type={}", type);
            return;
        }
        UserNotificationResponse response = userNotificationCacheService.getNotificationEnabled(userId);

        if (!response.isNotificationAllow()) {
            log.info("알림 수신 거부 userId={}", userId);
            return;
        }

        if (response.getSlackId() == null || response.getSlackId().isBlank()) {
            log.warn("slackId 없음, 알림 스킵 userId={}", userId);
            return;
        }

        NotificationLog notificationLog = NotificationLog.create(
                userId, type, title, message, referenceId, resolveReferenceType(type), response.getSlackId());
        notificationLogRepository.save(notificationLog);
        eventPublisher.publishEvent(new SlackNotificationEvent(notificationLog));
    }
}