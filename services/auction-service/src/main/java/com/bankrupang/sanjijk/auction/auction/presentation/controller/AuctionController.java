package com.bankrupang.sanjijk.auction.auction.presentation.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import com.bankrupang.sanjijk.auction.auction.application.service.AuctionService;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.request.AuctionCancelRequest;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.request.AuctionCloseRequest;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.request.AuctionCreateRequest;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.request.AuctionUpdateRequest;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionCancelResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionCloseResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionCreateResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionDetailResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionListResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionStartResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionUpdateResponse;
import com.bankrupang.sanjijk.common.response.ApiResponse;
import com.bankrupang.sanjijk.common.response.PageResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions")
public class AuctionController {

    private final AuctionService auctionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SELLER', 'MASTER')")
    public ResponseEntity<ApiResponse<AuctionCreateResponse>> createAuction(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole,
            @Valid @RequestBody AuctionCreateRequest request
    ) {
        AuctionCreateResponse response = auctionService.createAuction(userId, userRole, request);

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

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AuctionListResponse>>> getAuctions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) AuctionStatus status
    ) {
        PageResponse<AuctionListResponse> response = auctionService.getAuctions(page, size, status);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{auctionId}")
    @PreAuthorize("hasAnyRole('SELLER', 'MASTER', 'MANAGER')")
    public ResponseEntity<ApiResponse<AuctionUpdateResponse>> updateAuction(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable UUID auctionId,
            @Valid @RequestBody AuctionUpdateRequest request
    ) {
        AuctionUpdateResponse response = auctionService.updateAuction(userId, userRole, auctionId, request);

        return ResponseEntity.ok(ApiResponse.ok(response));

    }

    @PostMapping("/{auctionId}/cancel")
    @PreAuthorize("hasAnyRole('SELLER', 'MASTER', 'MANAGER')")
    public ResponseEntity<ApiResponse<AuctionCancelResponse>> cancelAuction(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable UUID auctionId,
            @Valid @RequestBody AuctionCancelRequest request
    ) {
        AuctionCancelResponse response = auctionService.cancelAuction(userId, userRole, auctionId, request);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{auctionId}/start")
    @PreAuthorize("hasAnyRole('MASTER', 'MANAGER')")
    public ResponseEntity<ApiResponse<AuctionStartResponse>> startAuctionManually(
            @PathVariable UUID auctionId
    ) {
        AuctionStartResponse response = auctionService.startAuctionManually(auctionId);

        return ResponseEntity.ok(ApiResponse.ok("경매가 시작되었습니다.", response));
    }

    @PostMapping("/{auctionId}/close")
    @PreAuthorize("hasAnyRole('MASTER', 'MANAGER')")
    public ResponseEntity<ApiResponse<AuctionCloseResponse>> closeAuctionManually(
            @PathVariable UUID auctionId,
            @Valid @RequestBody(required = false) AuctionCloseRequest request
    ) {
        AuctionCloseResponse response = auctionService.closeAuctionManually(auctionId, request);

        return ResponseEntity.ok(ApiResponse.ok("경매가 마감되었습니다.", response));
    }

}
