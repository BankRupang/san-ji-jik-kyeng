package com.bankrupang.sanjijk.bid.presentation.controller;

import com.bankrupang.sanjijk.bid.application.service.BidService;
import com.bankrupang.sanjijk.bid.presentation.dto.BidRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

    @MessageMapping("/auction/{auctionId}/bid")
    public void bid(
            @DestinationVariable UUID auctionId,
            @Header("X-User-Id") UUID userId,
            @Payload BidRequestDto request
    ) {
        //log.info("입찰 요청 - auctionId: {}, userId: {}, bidPrice: {}", auctionId, userId, request.getBidPrice());
        bidService.bid(auctionId, userId, request);
    }
}
