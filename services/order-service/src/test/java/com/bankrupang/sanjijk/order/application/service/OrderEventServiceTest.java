package com.bankrupang.sanjijk.order.application.service;

import com.bankrupang.sanjijk.order.domain.entity.Order;
import com.bankrupang.sanjijk.order.domain.entity.OrderOutbox;
import com.bankrupang.sanjijk.order.domain.exception.OrderEventPublishFailedException;
import com.bankrupang.sanjijk.order.infrastructure.outbox.OrderOutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("OrderEventService 테스트")
@ExtendWith(MockitoExtension.class)
class OrderEventServiceTest {

    @InjectMocks
    private OrderEventService orderEventService;

    @Mock
    private OrderOutboxJpaRepository orderOutboxJpaRepository;

    @Mock
    private ObjectMapper objectMapper;

    private Order depositOrder;
    private Order winningOrder;

    @BeforeEach
    void setUp() {
        depositOrder = Order.createDepositOrder(
                UUID.randomUUID(), "사람", "UI123456",
                UUID.randomUUID(), "싱싱한 광어", 10000);

        winningOrder = Order.createWinningOrder(
                UUID.randomUUID(), UUID.randomUUID(),
                "사람", "UI123456",
                UUID.randomUUID(), "싱싱한 광어",
                50000, null);
    }

    @Nested
    @DisplayName("publishDepositCreated()")
    class publishDepositCreated {

        @Test
        @DisplayName("T-ORD-WIN-04: 예치금 주문 Outbox 정상 저장")
        void publishDepositCreated() throws JsonProcessingException {
            // given
            given(objectMapper.writeValueAsString(any())).willReturn("{\"orderId\":\"test\"}");

            // when
            orderEventService.publishDepositCreated(depositOrder);

            // then
            ArgumentCaptor<OrderOutbox> captor = ArgumentCaptor.forClass(OrderOutbox.class);
            verify(orderOutboxJpaRepository).save(captor.capture());

            OrderOutbox saved = captor.getValue();
            assertThat(saved.getAggregateId()).isEqualTo(depositOrder.getId());
            assertThat(saved.getEventType()).isEqualTo("DEPOSIT_CREATED");
            assertThat(saved.getAggregateType()).isEqualTo("order");
        }

        @Test
        @DisplayName("JSON 직렬화 실패 시 OrderEventPublishFailedException 발생")
        void publishDepositCreatedFail() throws JsonProcessingException {
            // given
            given(objectMapper.writeValueAsString(any()))
                    .willThrow(new JsonProcessingException("직렬화 실패"){});

            // when + then
            assertThatThrownBy(() -> orderEventService.publishDepositCreated(depositOrder))
                    .isInstanceOf(OrderEventPublishFailedException.class);

            verify(orderOutboxJpaRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("publishWinningCreated()")
    class publishWinningCreated {

        @Test
        @DisplayName("낙찰 주문 Outbox 정상 저장")
        void publishWinningCreated() throws JsonProcessingException {
            // given
            given(objectMapper.writeValueAsString(any())).willReturn("{\"orderId\":\"test\"}");

            // when
            orderEventService.publishWinningCreated(winningOrder, 10000);

            // then
            ArgumentCaptor<OrderOutbox> captor = ArgumentCaptor.forClass(OrderOutbox.class);
            verify(orderOutboxJpaRepository).save(captor.capture());

            OrderOutbox saved = captor.getValue();
            assertThat(saved.getAggregateId()).isEqualTo(winningOrder.getId());
            assertThat(saved.getEventType()).isEqualTo("WINNING_CREATED");
            assertThat(saved.getAggregateType()).isEqualTo("order");
        }

        @Test
        @DisplayName("JSON 직렬화 실패 시 OrderEventPublishFailedException 발생")
        void publishWinningCreatedFail() throws JsonProcessingException {
            // given
            given(objectMapper.writeValueAsString(any()))
                    .willThrow(new JsonProcessingException("직렬화 실패"){});

            // when + then
            assertThatThrownBy(() -> orderEventService.publishWinningCreated(winningOrder, 10000))
                    .isInstanceOf(OrderEventPublishFailedException.class);

            verify(orderOutboxJpaRepository, never()).save(any());
        }
    }
}