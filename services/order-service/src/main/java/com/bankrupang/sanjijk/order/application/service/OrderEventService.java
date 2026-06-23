package com.bankrupang.sanjijk.order.application.service;

import com.bankrupang.sanjijk.order.application.port.OrderEventPublisher;
import com.bankrupang.sanjijk.order.domain.entity.Order;
import com.bankrupang.sanjijk.order.domain.entity.OrderOutbox;
import com.bankrupang.sanjijk.order.domain.exception.OrderEventPublishFailedException;
import com.bankrupang.sanjijk.order.infrastructure.outbox.OrderOutboxJpaRepository;
import com.bankrupang.sanjijk.order.infrastructure.messaging.producer.dto.DepositCreatedEvent;
import com.bankrupang.sanjijk.order.infrastructure.messaging.producer.dto.WinningCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderEventService implements OrderEventPublisher {

    private final OrderOutboxJpaRepository orderOutboxJpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void publishDepositCreated(Order order, LocalDateTime endAt) {
        DepositCreatedEvent event = new DepositCreatedEvent(
                order.getId(),
                order.getUserId(),
                order.getAuctionId(),
                order.getAuctionTitle(),
                order.getAmount(),
                endAt,
                LocalDateTime.now()
        );
        saveOutbox(order.getId(), "DEPOSIT_CREATED", event);
    }

    @Override
    @Transactional
    public void publishWinningCreated(Order order, int depositAmount) {
        WinningCreatedEvent event = new WinningCreatedEvent(
                order.getId(),
                order.getUserId(),
                order.getAuctionId(),
                order.getAuctionTitle(),
                order.getSellerId(),
                order.getAmount(),
                depositAmount,
                order.getAmount() - depositAmount,
                order.getPaymentDueAt(),
                LocalDateTime.now()
        );
        saveOutbox(order.getId(), "WINNING_CREATED", event);
    }

    private void saveOutbox(UUID aggregateId, String eventType, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OrderOutbox outbox = OrderOutbox.builder()
                    .aggregateType("order")
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .payload(payload)
                    .build();
            orderOutboxJpaRepository.save(outbox);
        } catch (JsonProcessingException e) {
            throw new OrderEventPublishFailedException();
        }
    }
}
