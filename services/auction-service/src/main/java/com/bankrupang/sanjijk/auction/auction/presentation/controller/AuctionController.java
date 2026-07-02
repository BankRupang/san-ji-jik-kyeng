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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

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
@Tag(name = "Auction API", description = "경매 관리 API")
public class AuctionController {

    private final AuctionService auctionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SELLER', 'MASTER')")
    @Operation(summary = "경매 등록", description = "판매자가 새로운 경매 일정을 등록합니다.")
    public ResponseEntity<ApiResponse<AuctionCreateResponse>> createAuction(
            @RequestHeader("X-User-Id") @Parameter(description = "사용자 ID") UUID userId,
            @RequestHeader("X-User-Role") @Parameter(description = "사용자 역할") String userRole,
            @Valid @RequestBody AuctionCreateRequest request
    ) {
        AuctionCreateResponse response = auctionService.createAuction(userId, userRole, request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("경매가 생성되었습니다.", response));
    }

    @GetMapping("/{auctionId}")
    @Operation(summary = "경매 단건 조회", description = "경매 상세 정보 및 실시간 최고가 현황을 조회합니다.")
    public ResponseEntity<ApiResponse<AuctionDetailResponse>> getAuction(
            @PathVariable @Parameter(description = "경매 ID") UUID auctionId
    ) {
        AuctionDetailResponse response = auctionService.getAuction(auctionId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    @Operation(summary = "경매 목록 조회", description = "진행 상태별 또는 전체 경매 목록을 페이징하여 조회합니다.")
    public ResponseEntity<ApiResponse<PageResponse<AuctionListResponse>>> getAuctions(
            @RequestParam(defaultValue = "0") @Parameter(description = "페이지 번호") int page,
            @RequestParam(defaultValue = "20") @Parameter(description = "페이지 크기") int size,
            @RequestParam(required = false) @Parameter(description = "경매 상태") AuctionStatus status
    ) {
        PageResponse<AuctionListResponse> response = auctionService.getAuctions(page, size, status);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{auctionId}")
    @PreAuthorize("hasAnyRole('SELLER', 'MASTER', 'MANAGER')")
    @Operation(summary = "경매 정보 수정", description = "경매가 시작되기 전(READY 상태)에 가격, 호가 단위 및 시작 시각을 수정합니다.")
    public ResponseEntity<ApiResponse<AuctionUpdateResponse>> updateAuction(
            @RequestHeader("X-User-Id") @Parameter(description = "사용자 ID") UUID userId,
            @RequestHeader("X-User-Role") @Parameter(description = "사용자 역할") String userRole,
            @PathVariable @Parameter(description = "경매 ID") UUID auctionId,
            @Valid @RequestBody AuctionUpdateRequest request
    ) {
        AuctionUpdateResponse response = auctionService.updateAuction(userId, userRole, auctionId, request);

        return ResponseEntity.ok(ApiResponse.ok(response));

    }

    @PostMapping("/{auctionId}/cancel")
    @PreAuthorize("hasAnyRole('SELLER', 'MASTER', 'MANAGER')")
    @Operation(summary = "경매 취소", description = "시작 대기 또는 진행 중인 경매를 취소 처리합니다.")
    public ResponseEntity<ApiResponse<AuctionCancelResponse>> cancelAuction(
            @RequestHeader("X-User-Id") @Parameter(description = "사용자 ID") UUID userId,
            @RequestHeader("X-User-Role") @Parameter(description = "사용자 역할") String userRole,
            @PathVariable @Parameter(description = "경매 ID") UUID auctionId,
            @Valid @RequestBody AuctionCancelRequest request
    ) {
        AuctionCancelResponse response = auctionService.cancelAuction(userId, userRole, auctionId, request);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{auctionId}/start")
    @PreAuthorize("hasAnyRole('MASTER', 'MANAGER')")
    @Operation(summary = "경매 수동 시작", description = "관리자가 경매를 강제로 PROGRESS 상태로 전환하고 이벤트를 발행합니다.")
    public ResponseEntity<ApiResponse<AuctionStartResponse>> startAuctionManually(
            @PathVariable @Parameter(description = "경매 ID") UUID auctionId
    ) {
        AuctionStartResponse response = auctionService.startAuctionManually(auctionId);

        return ResponseEntity.ok(ApiResponse.ok("경매가 시작되었습니다.", response));
    }

    @PostMapping("/{auctionId}/close")
    @PreAuthorize("hasAnyRole('MASTER', 'MANAGER')")
    @Operation(summary = "경매 수동 마감", description = "관리자가 경매를 조기 마감합니다. forceFail 플래그에 따라 강제 유찰 또는 최고가 입찰자로 자동 낙찰 처리됩니다.")
    public ResponseEntity<ApiResponse<AuctionCloseResponse>> closeAuctionManually(
            @PathVariable @Parameter(description = "경매 ID") UUID auctionId,
            @Valid @RequestBody(required = false) AuctionCloseRequest request
    ) {
        AuctionCloseResponse response = auctionService.closeAuctionManually(auctionId, request);

        return ResponseEntity.ok(ApiResponse.ok("경매가 마감되었습니다.", response));
    }

}
