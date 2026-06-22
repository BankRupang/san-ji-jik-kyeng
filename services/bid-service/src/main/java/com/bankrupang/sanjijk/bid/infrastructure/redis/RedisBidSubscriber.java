package com.bankrupang.sanjijk.bid.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisBidSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());

        String[] parts = channel.split(":");
        if (parts.length < 2) return;

        String auctionId = parts[1];
        log.info("Redis Pub/Sub 수신 - auctionId: {}, body: {}", auctionId, body);

        messagingTemplate.convertAndSend("/topic/auction/" + auctionId, body);
    }
}
