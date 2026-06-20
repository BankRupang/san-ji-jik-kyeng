package com.bankrupang.sanjijk.order.application.service;

import com.bankrupang.sanjijk.order.domain.entity.Order;
import com.bankrupang.sanjijk.order.domain.enums.OrderStatus;
import com.bankrupang.sanjijk.order.domain.enums.OrderType;
import com.bankrupang.sanjijk.order.domain.exception.DuplicateOrderException;
import com.bankrupang.sanjijk.order.domain.exception.OrderNotFoundException;
import com.bankrupang.sanjijk.order.domain.repository.OrderRepository;
import com.bankrupang.sanjijk.order.infrastructure.feign.dto.UserInfoResponse;
import com.bankrupang.sanjijk.order.infrastructure.messaging.consumer.dto.*;
import com.bankrupang.sanjijk.order.presentation.dto.request.OrderDepositCreateRequest;
import com.bankrupang.sanjijk.order.presentation.dto.response.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventService orderEventService;


    @Transactional
    public OrderResponse createDepositOrder(UUID userId, OrderDepositCreateRequest request) {
        // 1. 멱등성 체크(동일한 유저가 같은 경매에 이미 보증금 주문 있는지 확인)
        orderRepository.findByUserIdAndAuctionIdAndOrderType(userId, request.auctionId(), OrderType.DEPOSIT)
                .ifPresent(o -> {
                    log.warn("중복 보증금 주문 요청 - userId {}, auctionId: {}", userId, request.auctionId());
                    throw new DuplicateOrderException();
                });

        // 2. 보증금 주문 생성 및 저장(pending 기본 값)
        Order order = Order.createDepositOrder(
                userId,
                request.userName(),
                request.slackId(),
                request.auctionId(),
                request.auctionTitle(),
                request.amount()
        );

        // 멱등성 체크(동시에 들어올 경우)
        try{
            orderRepository.save(order);
        } catch (DataIntegrityViolationException e) {
            log.warn("중복 보증금 주문 - userId {}, auctionId: {}", userId, request.auctionId());
            throw new DuplicateOrderException();
        }
        log.info("보증금 주문 생성 완료 - orderId: {}, userId: {}, auctionId: {}", order.getId(), userId, request.auctionId());

        // 3. 결제 요청 이벤트 발행
        orderEventService.publishDepositCreated(order);

        return OrderResponse.from(order);
    };

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
                userInfo.userName(),
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

    @Transactional
    public void expireDepositOrders(AuctionFailedEvent event) {
        List<Order> depositOrders = orderRepository.findAllByAuctionIdAndOrderType(
                event.auctionId(), OrderType.DEPOSIT);

        for (Order order : depositOrders) {
            order.markRefunded();
            log.info("[ORDER] 유찰 환불 처리 - orderId: {}, userId: {}, auctionId: {}",
                    order.getId(), order.getUserId(), event.auctionId());
        }
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
            log.info("[ORDER] 예치금 결제 완료 - orderId: {}, userId: {}", order.getId(), order.getUserId());

        } else if (order.getOrderType() == OrderType.WINNING) {
            if (order.getStatus() == OrderStatus.COMPLETED) {
                log.warn("[ORDER] PAYMENT_COMPLETED 중복 수신 - orderId: {}", event.orderId());
                return;
            }
            if ("REPAY".equals(event.paymentType())) {
                order.markPenaltyCompleted();
            } else {
                order.markPaymentSuccess();
                order.markCompleted();
            }
            log.info("[ORDER] 낙찰 결제 완료 - orderId: {}, userId: {}", order.getId(), order.getUserId());
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
        log.warn("[ORDER] 결제 실패 → PENALTY_PENDING - orderId: {}, userId: {}, penaltyDueAt: {}",
                order.getId(), order.getUserId(), order.getPenaltyDueAt());
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
        log.warn("[ORDER] 예치금 몰수 완료 - orderId: {}, userId: {}", order.getId(), order.getUserId());
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
        log.info("[ORDER] 예치금 환불 완료 - orderId: {}, userId: {}", order.getId(), order.getUserId());
    }
}
