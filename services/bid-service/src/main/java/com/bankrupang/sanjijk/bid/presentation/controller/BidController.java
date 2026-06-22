package com.bankrupang.sanjijk.bid.presentation.controller;

import com.bankrupang.sanjijk.bid.application.service.BidService;
import com.bankrupang.sanjijk.bid.domain.exception.BidException;
import com.bankrupang.sanjijk.bid.presentation.dto.BidRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/auction/{auctionId}/bid")
    public void bid(
            @DestinationVariable UUID auctionId,
            @Payload BidRequestDto request,
            Principal principal
    ) {
        UUID userId = UUID.fromString(principal.getName());
        log.info("입찰 요청 - auctionId: {}, userId: {}, bidPrice: {}", auctionId, userId, request.getBidPrice());
        try {
            bidService.bid(auctionId, userId, request);
        } catch (BidException e) {
            log.warn("입찰 실패 - userId: {}, code: {}, message: {}", userId, e.getErrorCode().getCode(), e.getMessage());
            messagingTemplate.convertAndSendToUser(
                    principal.getName(),
                    "/queue/errors",
                    Map.of("type", "BID_FAILED", "code", e.getErrorCode().getCode(), "message", e.getMessage())
            );
        }
    }
}
