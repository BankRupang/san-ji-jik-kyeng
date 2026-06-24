package com.bankrupang.sanjijk.order.application.service;

import com.bankrupang.sanjijk.order.domain.entity.Order;
import com.bankrupang.sanjijk.order.domain.enums.OrderStatus;
import com.bankrupang.sanjijk.order.domain.enums.OrderType;
import com.bankrupang.sanjijk.order.domain.exception.DuplicateOrderException;
import com.bankrupang.sanjijk.order.domain.exception.OrderNotFoundException;
import com.bankrupang.sanjijk.order.domain.repository.OrderRepository;
import com.bankrupang.sanjijk.order.infrastructure.feign.AuctionClient;
import com.bankrupang.sanjijk.order.infrastructure.feign.UserClient;
import com.bankrupang.sanjijk.order.infrastructure.feign.dto.AuctionInfoResponse;
import com.bankrupang.sanjijk.order.infrastructure.feign.dto.UserInfoResponse;
import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.*;
import com.bankrupang.sanjijk.order.presentation.dto.request.OrderDepositCreateRequest;
import com.bankrupang.sanjijk.order.presentation.dto.response.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventService orderEventService;
    private final AuctionClient auctionClient;
    private final UserClient userClient;


    @Transactional
    public OrderResponse createDepositOrder(UUID userId, OrderDepositCreateRequest request) {
        // 1. 멱등성 체크
        orderRepository.findByUserIdAndAuctionIdAndOrderType(userId, request.auctionId(), OrderType.DEPOSIT)
                .ifPresent(o -> {
                    log.warn("중복 보증금 주문 요청 - userId {}, auctionId: {}", userId, request.auctionId());
                    throw new DuplicateOrderException();
                });

        // 2. auction-service / user-service에서 신뢰할 수 있는 데이터 조회
        AuctionInfoResponse auction = auctionClient.getAuction(request.auctionId());
        UserInfoResponse userInfo = userClient.getUserInfo(userId);

        // 3. 보증금 주문 생성 및 저장
        Order order = Order.createDepositOrder(
                userId,
                userInfo.name(),
                userInfo.slackId(),
                auction.auctionId(),
                auction.auctionTitle(),
                auction.depositAmount()
        );

        try {
            orderRepository.save(order);
        } catch (DataIntegrityViolationException e) {
            log.warn("중복 보증금 주문 - userId {}, auctionId: {}", userId, request.auctionId());
            throw new DuplicateOrderException();
        }
        log.info("보증금 주문 생성 완료 - orderId: {}, userId: {}, auctionId: {}", order.getId(), userId, request.auctionId());

        // 4. 결제 요청 이벤트 발행
        orderEventService.publishDepositCreated(order, auction.endAt());

        return OrderResponse.from(order);
    }

    // 내 보증금 주문 목록 조회
    public Page<OrderResponse> getMyDepositOrders(UUID userId, Pageable pageable) {
        return orderRepository.findAllByUserIdAndOrderType(userId, OrderType.DEPOSIT, pageable)
                .map(OrderResponse::from);
    }

    // 내 낙찰 주문 목록 조회
    public Page<OrderResponse> getMyWinningOrders(UUID userId, Pageable pageable) {
        return orderRepository.findAllByUserIdAndOrderType(userId, OrderType.WINNING, pageable)
                .map(OrderResponse::from);
    }

    // 멱등성 체크
    public boolean existsWinningOrder(UUID auctionId, UUID winnerId) {
        return orderRepository.findByUserIdAndAuctionIdAndOrderType(winnerId, auctionId, OrderType.WINNING)
                .isPresent();
    }

    // 낙찰 주문 생성
    @Transactional
    public void createWinningOrder(AuctionWonEvent event, UserInfoResponse userInfo) {
        Order order = Order.createWinningOrder(
                event.winnerId(),
                event.sellerId(),
                userInfo.name(),
                userInfo.slackId(),
                event.auctionId(),
                event.auctionTitle(),
                event.finalPrice(),
                null
        );

        try {
            orderRepository.save(order);
        } catch (DataIntegrityViolationException e) {
            log.warn("[ORDER] 중복 낙찰 주문 - auctionId: {}, winnerId: {}", event.auctionId(), event.winnerId());
            throw new DuplicateOrderException();
        }

        log.info("[ORDER] 낙찰 주문 생성 완료 - orderId: {}, winnerId: {}, auctionId: {}",
                order.getId(), event.winnerId(), event.auctionId());

        orderEventService.publishWinningCreated(order, event.depositAmount());
    }

    // PAYMENT_COMPLETED 처리
    @Transactional
    public void completePayment(PaymentCompletedEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> {
                    log.error("[ORDER] 주문 없음 - orderId: {}", event.orderId());
                    return new OrderNotFoundException();
                });

        if (order.getOrderType() == OrderType.DEPOSIT) {
            if (order.getStatus() == OrderStatus.PAYMENT_SUCCESS) {
                log.warn("[ORDER] PAYMENT_COMPLETED 중복 수신 - orderId: {}", event.orderId());
                return;
            }
            order.markPaymentSuccess();
            log.info("[ORDER] 예치금 결제 완료 - orderId: {}, userId: {}, auctionId: {}, orderType: {}, PENDING → PAYMENT_SUCCESS",
                    order.getId(), order.getUserId(), event.auctionId(), order.getOrderType());

        } else if (order.getOrderType() == OrderType.WINNING) {
            if (order.getStatus() == OrderStatus.COMPLETED) {
                log.warn("[ORDER] PAYMENT_COMPLETED 중복 수신 - orderId: {}", event.orderId());
                return;
            }
            if ("REPAY".equals(event.paymentType())) {
                order.markPenaltyCompleted();
                log.info("[ORDER] 낙찰 재결제 완료 - orderId: {}, userId: {}, auctionId: {}, orderType: {}, PENALTY_PENDING → COMPLETED",
                        order.getId(), order.getUserId(), event.auctionId(), order.getOrderType());
            } else {
                order.markPaymentSuccess();
                order.markCompleted();
                log.info("[ORDER] 낙찰 결제 완료 - orderId: {}, userId: {}, auctionId: {}, orderType: {}, PENDING → PAYMENT_SUCCESS → COMPLETED",
                        order.getId(), order.getUserId(), event.auctionId(), order.getOrderType());
            }
        }
    }

    // PAYMENT_FAILED 처리
    @Transactional
    public void failPayment(PaymentFailedEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> {
                    log.error("[ORDER] 주문 없음 - orderId: {}", event.orderId());
                    return new OrderNotFoundException();
                });

        if (order.getOrderType() != OrderType.WINNING) {
            log.warn("[ORDER] WINNING 주문 아님 - orderId: {}, orderType: {}", event.orderId(), order.getOrderType());
            return;
        }
        if (order.getStatus() == OrderStatus.PENALTY_PENDING) {
            log.warn("[ORDER] PAYMENT_FAILED 중복 수신 - orderId: {}", event.orderId());
            return;
        }

        order.markPaymentFailed();
        order.markPenaltyPending();
        log.warn("[ORDER] 결제 실패 - orderId: {}, userId: {}, auctionId: {}, orderType: {}, PENDING → PAYMENT_FAILED → PENALTY_PENDING, penaltyDueAt: {}",
                order.getId(), order.getUserId(), event.auctionId(), order.getOrderType(), order.getPenaltyDueAt());
    }

    // DEPOSIT_FORFEITED 처리
    @Transactional
    public void forfeitDeposit(DepositForfeitedEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> {
                    log.error("[ORDER] 주문 없음 - orderId: {}", event.orderId());
                    return new OrderNotFoundException();
                });

        if (order.getOrderType() != OrderType.DEPOSIT) {
            log.warn("[ORDER] DEPOSIT 주문 아님 - orderId: {}, orderType: {}", event.orderId(), order.getOrderType());
            return;
        }
        if (order.getStatus() == OrderStatus.FORFEITED) {
            log.warn("[ORDER] DEPOSIT_FORFEITED 중복 수신 - orderId: {}", event.orderId());
            return;
        }

        order.markForfeited();
        log.warn("[ORDER] 예치금 몰수 - orderId: {}, userId: {}, auctionId: {}, orderType: {}, PAYMENT_SUCCESS → FORFEITED",
                order.getId(), order.getUserId(), event.auctionId(), order.getOrderType());
    }

    // REFUND_COMPLETED 처리
    @Transactional
    public void completeRefund(RefundCompletedEvent event) {
        Order order = orderRepository.findById(event.orderId())
                .orElseThrow(() -> {
                    log.error("[ORDER] 주문 없음 - orderId: {}", event.orderId());
                    return new OrderNotFoundException();
                });

        if (order.getOrderType() != OrderType.DEPOSIT) {
            log.warn("[ORDER] DEPOSIT 주문 아님 - orderId: {}, orderType: {}", event.orderId(), order.getOrderType());
            return;
        }
        if (order.getStatus() == OrderStatus.REFUNDED) {
            log.warn("[ORDER] REFUND_COMPLETED 중복 수신 - orderId: {}", event.orderId());
            return;
        }

        order.markRefunded();
        log.info("[ORDER] 예치금 환불 완료 - orderId: {}, userId: {}, auctionId: {}, orderType: {}, PAYMENT_SUCCESS → REFUNDED",
                order.getId(), order.getUserId(), event.auctionId(), order.getOrderType());
    }
}
