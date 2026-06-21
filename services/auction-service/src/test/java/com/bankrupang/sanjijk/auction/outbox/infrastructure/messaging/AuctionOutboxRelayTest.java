package com.bankrupang.sanjijk.auction.outbox.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import com.bankrupang.sanjijk.auction.outbox.domain.entity.AuctionOutbox;
import com.bankrupang.sanjijk.auction.outbox.domain.repository.AuctionOutboxRepository;
import com.bankrupang.sanjijk.auction.outbox.domain.type.AuctionEventType;

@DisplayName("AuctionOutboxRelay 테스트")
@ExtendWith(MockitoExtension.class)
class AuctionOutboxRelayTest {

    @InjectMocks
    private AuctionOutboxRelay auctionOutboxRelay;

    @Mock
    private AuctionOutboxRepository auctionOutboxRepository;

    @Mock
    private AuctionEventTopicResolver auctionEventTopicResolver;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Nested
    @DisplayName("publishPendingEvents()")
    class PublishPendingEvents {

        @Test
        @DisplayName("성공 - 미발행 이벤트를 Kafka로 발행하고 발행 완료 처리한다")
        @SuppressWarnings("unchecked")
        void success() {
            // given
            int batchSize = 50;
            UUID auctionId = UUID.randomUUID();
            String payload = "{\"auctionId\":\"" + auctionId + "\"}";
            AuctionOutbox outbox = AuctionOutbox.create(
                    auctionId,
                    AuctionEventType.AUCTION_START,
                    payload
            );

            ReflectionTestUtils.setField(auctionOutboxRelay, "batchSize", batchSize);
            given(auctionOutboxRepository.findByPublishedFalseOrderByCreatedAtAsc(any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(outbox)));
            given(auctionEventTopicResolver.resolve(AuctionEventType.AUCTION_START))
                    .willReturn("auction-start");
            given(kafkaTemplate.send("auction-start", auctionId.toString(), payload))
                    .willReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

            // when
            auctionOutboxRelay.publishPendingEvents();

            // then
            assertThat(outbox.isPublished()).isTrue();
            assertThat(outbox.getPublishedAt()).isNotNull();

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(auctionOutboxRepository).findByPublishedFalseOrderByCreatedAtAsc(pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(batchSize);
            verify(auctionEventTopicResolver).resolve(AuctionEventType.AUCTION_START);
            verify(kafkaTemplate).send("auction-start", auctionId.toString(), payload);
        }

        @Test
        @DisplayName("실패 - Kafka 발행 실패 시 발행 완료 처리하지 않는다")
        void fail_publish_event() {
            // given
            UUID auctionId = UUID.randomUUID();
            String payload = "{\"auctionId\":\"" + auctionId + "\"}";
            AuctionOutbox outbox = AuctionOutbox.create(
                    auctionId,
                    AuctionEventType.AUCTION_WON,
                    payload
            );

            ReflectionTestUtils.setField(auctionOutboxRelay, "batchSize", 100);
            given(auctionOutboxRepository.findByPublishedFalseOrderByCreatedAtAsc(any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(outbox)));
            given(auctionEventTopicResolver.resolve(AuctionEventType.AUCTION_WON))
                    .willReturn("auction-events");
            given(kafkaTemplate.send("auction-events", auctionId.toString(), payload))
                    .willReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka 발행 실패")));

            // when
            auctionOutboxRelay.publishPendingEvents();

            // then
            assertThat(outbox.isPublished()).isFalse();
            assertThat(outbox.getPublishedAt()).isNull();
            verify(kafkaTemplate).send("auction-events", auctionId.toString(), payload);
        }
    }
}
