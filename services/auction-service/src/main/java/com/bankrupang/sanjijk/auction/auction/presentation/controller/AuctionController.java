package com.bankrupang.sanjijk.auction.auction.presentation.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import com.bankrupang.sanjijk.auction.auction.application.service.AuctionService;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.request.AuctionCreateRequest;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionCreateResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionDetailResponse;
import com.bankrupang.sanjijk.common.response.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions")
public class AuctionController {

    private final AuctionService auctionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SELLER', 'MASTER')")
    public ResponseEntity<ApiResponse<AuctionCreateResponse>> createAuction(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody AuctionCreateRequest request
    ) {
        AuctionCreateResponse response = auctionService.createAuction(userId, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("경매가 생성되었습니다.", response));
    }

    @GetMapping("/{auctionId}")
    public ResponseEntity<ApiResponse<AuctionDetailResponse>> getAuction(
            @PathVariable UUID auctionId
    ) {
        AuctionDetailResponse response = auctionService.getAuction(auctionId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

}
