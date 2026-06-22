package com.bankrupang.sanjijk.auction.outbox.application.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.outbox.application.dto.AuctionFailedEventPayload;
import com.bankrupang.sanjijk.auction.outbox.application.dto.AuctionStartEventPayload;
import com.bankrupang.sanjijk.auction.outbox.application.dto.AuctionWonEventPayload;
import com.bankrupang.sanjijk.auction.outbox.domain.entity.AuctionOutbox;
import com.bankrupang.sanjijk.auction.outbox.domain.repository.AuctionOutboxRepository;
import com.bankrupang.sanjijk.auction.outbox.domain.type.AuctionEventType;
import com.bankrupang.sanjijk.auction.product.domain.entity.Product;

@Service
@RequiredArgsConstructor
public class AuctionOutboxService {

    private final AuctionOutboxRepository auctionOutboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveAuctionStartEvent(Auction auction, Product product) {
        AuctionStartEventPayload payload = AuctionStartEventPayload.of(auction, product);

        saveEvent(auction.getId(), AuctionEventType.AUCTION_START, payload);
    }

    @Transactional
    public void saveAuctionWonEvent(Auction auction, Product product) {
        AuctionWonEventPayload payload = AuctionWonEventPayload.of(
                auction,
                product,
                LocalDateTime.now()
        );

        saveEvent(auction.getId(), AuctionEventType.AUCTION_WON, payload);
    }

    @Transactional
    public void saveAuctionFailedEvent(Auction auction, Product product) {
        AuctionFailedEventPayload payload = AuctionFailedEventPayload.of(
                auction,
                product,
                LocalDateTime.now()
        );

        saveEvent(auction.getId(), AuctionEventType.AUCTION_FAILED, payload);
    }

    private void saveEvent(UUID aggregateId, AuctionEventType eventType, Object payload) {
        String payloadJson = serializePayload(payload);
        AuctionOutbox outbox = AuctionOutbox.create(aggregateId, eventType, payloadJson);

        auctionOutboxRepository.save(outbox);
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("경매 이벤트 payload JSON 변환에 실패했습니다.", e);
        }
    }
}
