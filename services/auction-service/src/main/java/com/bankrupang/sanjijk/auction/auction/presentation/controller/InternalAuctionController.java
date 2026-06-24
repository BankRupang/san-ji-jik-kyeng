package com.bankrupang.sanjijk.auction.auction.presentation.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.bankrupang.sanjijk.auction.auction.application.service.AuctionService;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionDepositInfoResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/auctions")
@Tag(name = "Internal Auction API", description = "서버 간 직접 호출 전용 내부 경매 API")
public class InternalAuctionController {

    private final AuctionService auctionService;

    @GetMapping("/{auctionId}")
    @Operation(summary = "내부 경매 보증금 정보 조회", description = "order-service 등 내부 서비스에서 보증금 생성 시 검증하기 위해 경매의 보증금액, 상품명, 종료 시각을 직접 조회합니다.")
    public AuctionDepositInfoResponse getAuctionDepositInfo(
            @PathVariable @Parameter(description = "경매 ID") UUID auctionId
    ) {
        return auctionService.getAuctionDepositInfo(auctionId);
    }
}
