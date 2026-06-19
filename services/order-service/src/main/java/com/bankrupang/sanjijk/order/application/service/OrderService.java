package com.bankrupang.sanjijk.order.application.service;

import com.bankrupang.sanjijk.order.application.port.OrderEventPublisher;
import com.bankrupang.sanjijk.order.domain.entity.Order;
import com.bankrupang.sanjijk.order.domain.enums.OrderType;
import com.bankrupang.sanjijk.order.domain.exception.DuplicateOrderException;
import com.bankrupang.sanjijk.order.domain.repository.OrderRepository;
import com.bankrupang.sanjijk.order.presentation.dto.request.OrderDepositCreateRequest;
import com.bankrupang.sanjijk.order.presentation.dto.response.OrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;


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
        // TODO: outbox 패턴 적용 필(kakfa 구현 쪽이랑 동시에 진행할 것)
        orderEventPublisher.publishDepositCreated(order);

        return OrderResponse.from(order);
    };
}
