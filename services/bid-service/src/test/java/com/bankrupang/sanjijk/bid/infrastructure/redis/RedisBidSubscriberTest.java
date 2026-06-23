package com.bankrupang.sanjijk.bid.infrastructure.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.mockito.Mockito.*;

/**
 * 시나리오 4.1 - Redis Pub/Sub → WebSocket 브로드캐스팅
 * WebSocket은 검증 없이 Pub/Sub 이벤트만 전파하는 역할임을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("시나리오 4.1 - Redis Pub/Sub WebSocket 브로드캐스팅")
class RedisBidSubscriberTest {

    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private RedisBidSubscriber subscriber;

    @Test
    @DisplayName("bid-event 채널 메시지를 /topic/auction/{auctionId}로 브로드캐스트")
    void shouldBroadcast_whenValidChannelMessage() {
        String auctionId = "550e8400-e29b-41d4-a716-446655440000";
        String channel = "auction:" + auctionId + ":bid-event";
        String body = "{\"type\":\"BID_UPDATED\",\"currentPrice\":11000,\"nextMinPrice\":12000}";

        Message message = mock(Message.class);
        when(message.getChannel()).thenReturn(channel.getBytes());
        when(message.getBody()).thenReturn(body.getBytes());

        subscriber.onMessage(message, null);

        verify(messagingTemplate).convertAndSend("/topic/auction/" + auctionId, body);
    }

    @Test
    @DisplayName("채널 파싱 불가(콜론 없음)면 브로드캐스트 없음")
    void shouldIgnore_whenInvalidChannel() {
        Message message = mock(Message.class);
        when(message.getChannel()).thenReturn("invalid".getBytes());
        when(message.getBody()).thenReturn("{}".getBytes());

        subscriber.onMessage(message, null);

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("AUCTION_ENDED 타입도 동일하게 브로드캐스트")
    void shouldBroadcast_whenAuctionEndedEvent() {
        String auctionId = "550e8400-e29b-41d4-a716-446655440000";
        String channel = "auction:" + auctionId + ":bid-event";
        String body = "{\"type\":\"AUCTION_ENDED\"}";

        Message message = mock(Message.class);
        when(message.getChannel()).thenReturn(channel.getBytes());
        when(message.getBody()).thenReturn(body.getBytes());

        subscriber.onMessage(message, null);

        verify(messagingTemplate).convertAndSend("/topic/auction/" + auctionId, body);
    }
}
