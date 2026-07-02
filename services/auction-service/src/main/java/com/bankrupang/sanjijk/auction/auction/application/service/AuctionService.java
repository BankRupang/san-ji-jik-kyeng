package com.bankrupang.sanjijk.auction.auction.application.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.repository.AuctionRepository;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.auction.auction.exception.AuctionErrorCode;
import com.bankrupang.sanjijk.auction.auction.exception.AuctionException;
import com.bankrupang.sanjijk.auction.auction.infrastructure.scheduler.AuctionScheduleManager;
import com.bankrupang.sanjijk.auction.auction.infrastructure.scheduler.AuctionSchedulerJobService;
import com.bankrupang.sanjijk.auction.auction.infrastructure.transaction.TransactionAfterCommitExecutor;
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
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionDepositInfoResponse;
import com.bankrupang.sanjijk.auction.global.util.AuctionLogContext;
import com.bankrupang.sanjijk.auction.outbox.application.service.AuctionOutboxService;
import com.bankrupang.sanjijk.auction.product.domain.entity.Product;
import com.bankrupang.sanjijk.auction.product.domain.repository.ProductRepository;
import com.bankrupang.sanjijk.auction.product.exception.ProductErrorCode;
import com.bankrupang.sanjijk.auction.product.exception.ProductException;
import com.bankrupang.sanjijk.auction.auction.infrastructure.client.BidClient;
import com.bankrupang.sanjijk.auction.auction.infrastructure.client.dto.HighestBidResponse;
import com.bankrupang.sanjijk.common.response.PageResponse;
import com.bankrupang.sanjijk.common.util.PageableUtils;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final ProductRepository productRepository;
    private final AuctionOutboxService auctionOutboxService;
    private final AuctionScheduleManager auctionScheduleManager;
    private final AuctionSchedulerJobService auctionSchedulerJobService;
    private final TransactionAfterCommitExecutor transactionAfterCommitExecutor;
    private final BidClient bidClient;

    @Transactional
    public AuctionCreateResponse createAuction(UUID userId, String userRole, AuctionCreateRequest request) {
        Product product = getExistingProduct(request.productId());
        validateProductOwnerOrMaster(product, userId, userRole);
        validateStartAt(request.startAt());
        validateDuplicateAuction(product.getId());

        LocalDateTime endAt = request.startAt().plusHours(1);

        Auction auction = Auction.create(
                product.getId(),
                product.getSellerId(),
                request.startPrice(),
                request.bidUnit(),
                request.startAt(),
                endAt
        );

        Auction savedAuction = auctionRepository.save(auction);
        scheduleStartJobAfterCommit(savedAuction);

        return AuctionCreateResponse.from(savedAuction);
    }

    public AuctionDetailResponse getAuction(UUID auctionId) {
        Auction auction = getExistingAuction(auctionId);
        Product product = getExistingProduct(auction.getProductId());

        if (auction.getStatus() == AuctionStatus.PROGRESS) {
            try {
                HighestBidResponse highestBid = bidClient.getHighestBid(auctionId);
                if (highestBid != null) {
                    return AuctionDetailResponse.of(auction, product, highestBid.winnerId(), highestBid.finalPrice());
                }
            } catch (Exception e) {
                log.warn("경매 상세 조회 중 bid-service 최고가 조회 실패 - auctionId: {}, error: {}", auctionId, e.getMessage());
            }
        }

        return AuctionDetailResponse.of(auction, product);
    }

    public PageResponse<AuctionListResponse> getAuctions(int page, int size, AuctionStatus status) {
        Pageable pageable = PageableUtils.ofDefault(page, size);

        Page<Auction> auctions = findAuctions(pageable, status);
        Map<UUID, Product> productMap = getProductMap(auctions);

        List<AuctionListResponse> content = auctions.getContent().stream()
                .map(auction -> {
                    Product product = productMap.get(auction.getProductId());
                    if (product == null) {
                        return null;
                    }

                    return AuctionListResponse.of(auction, product);
                })
                .filter(Objects::nonNull)
                .toList();

        Page<AuctionListResponse> response = new PageImpl<>(content, pageable, content.size());

        return PageResponse.of(response);
    }

    @Transactional
    public AuctionUpdateResponse updateAuction(UUID userId, String userRole, UUID auctionId, AuctionUpdateRequest request) {
        validateUpdateRequest(request);

        Auction auction = getExistingAuction(auctionId);
        validateAuctionOwnerOrMasterOrManager(auction, userId, userRole);
        auction.validateEditable();

        auction.update(
                request.startPrice(),
                request.bidUnit(),
                request.startAt()
        );

        if (request.startAt() != null) {
            scheduleStartJobAfterCommit(auction);
        }

        return AuctionUpdateResponse.from(auction);
    }

    @Transactional
    public AuctionCancelResponse cancelAuction(UUID userId, String userRole, UUID auctionId, AuctionCancelRequest request) {
        Auction auction = getExistingAuction(auctionId);
        validateAuctionOwnerOrMasterOrManager(auction, userId, userRole);
        auction.validateEditable();

        auction.cancel(request.reason());
        cancelStartJobAfterCommit(auction);

        return AuctionCancelResponse.from(auction);
    }

    @Transactional
    public AuctionStartResponse startAuctionManually(UUID auctionId) {
        Auction auction = getExistingAuction(auctionId);

        auction.start();
        Product product = getExistingProduct(auction.getProductId());
        auctionOutboxService.saveAuctionStartEvent(auction, product);

        return AuctionStartResponse.from(auction);
    }

    @Transactional
    public AuctionCloseResponse closeAuctionManually(UUID auctionId, AuctionCloseRequest request) {
        return AuctionLogContext.callWithAuctionId(auctionId, () -> {
            Auction auction = getExistingAuction(auctionId);

            if (request != null && Boolean.TRUE.equals(request.forceFail())) {
                return closeAuction(auction, null, null, "MANUAL_FORCE_FAIL");
            }

            HighestBidResponse response = bidClient.getHighestBid(auctionId);
            boolean hasBid = response != null && response.winnerId() != null && response.finalPrice() != null;
            UUID winnerId = hasBid ? response.winnerId() : null;
            Integer finalPrice = hasBid ? response.finalPrice() : null;

            return closeAuction(auction, winnerId, finalPrice, "MANUAL_CLOSE");
        });
    }

    @Transactional
    public AuctionCloseResponse closeAuctionByEndedEvent(UUID auctionId, boolean hasBid, UUID winnerId, Integer finalPrice) {
        return AuctionLogContext.callWithAuctionId(auctionId, () -> {
            Auction auction = getExistingAuction(auctionId);
            UUID finalWinnerId = hasBid ? winnerId : null;
            Integer finalWinningPrice = hasBid ? finalPrice : null;

            return closeAuction(auction, finalWinnerId, finalWinningPrice, "AUCTION_ENDED");
        });
    }



    @Transactional
    public void completeAuctionPayment(UUID auctionId) {
        AuctionLogContext.runWithAuctionId(auctionId, () -> {
            Auction auction = getExistingAuction(auctionId);

            if (auction.getStatus() == AuctionStatus.SUCCESS || auction.getStatus() == AuctionStatus.FAIL) {
                log.info("결제 완료 이벤트 처리 생략 - 이미 최종 상태입니다. auctionId: {}, status: {}",
                        auctionId, auction.getStatus());
                return;
            }

            AuctionStatus previousStatus = auction.getStatus();
            auction.markSuccess();
            log.info("경매 상태 전이 - auctionId: {}, trigger: PAYMENT_COMPLETED, previousStatus: {}, currentStatus: {}",
                    auctionId, previousStatus, auction.getStatus());
        });
    }

    @Transactional
    public void failAuctionPayment(UUID auctionId) {
        AuctionLogContext.runWithAuctionId(auctionId, () -> {
            Auction auction = getExistingAuction(auctionId);

            if (auction.getStatus() == AuctionStatus.SUCCESS || auction.getStatus() == AuctionStatus.FAIL) {
                log.info("결제 실패 이벤트 처리 생략 - 이미 최종 상태입니다. auctionId: {}, status: {}",
                        auctionId, auction.getStatus());
                return;
            }

            AuctionStatus previousStatus = auction.getStatus();
            auction.markFailed();
            log.info("경매 상태 전이 - auctionId: {}, trigger: PAYMENT_FAILED, previousStatus: {}, currentStatus: {}",
                    auctionId, previousStatus, auction.getStatus());
        });
    }

    private AuctionCloseResponse closeAuction(Auction auction, UUID winnerId, Integer finalPrice, String trigger) {
        if (isAlreadyClosed(auction)) {
            log.info("경매 마감 처리 생략 - 이미 종료된 상태입니다. auctionId: {}, trigger: {}, status: {}",
                    auction.getId(), trigger, auction.getStatus());
            return AuctionCloseResponse.from(auction);
        }

        validateCloseRequest(auction, winnerId, finalPrice);

        AuctionStatus previousStatus = auction.getStatus();
        auction.markResultPending();
        log.info("경매 상태 전이 - auctionId: {}, trigger: {}, previousStatus: {}, currentStatus: {}",
                auction.getId(), trigger, previousStatus, auction.getStatus());
        Product product = getExistingProduct(auction.getProductId());

        if (winnerId != null && finalPrice != null) {
            previousStatus = auction.getStatus();
            auction.markWon(winnerId, finalPrice);
            log.info("경매 상태 전이 - auctionId: {}, trigger: {}, previousStatus: {}, currentStatus: {}, winnerId: {}, finalPrice: {}",
                    auction.getId(), trigger, previousStatus, auction.getStatus(), auction.getWinnerId(), auction.getFinalPrice());
            auctionOutboxService.saveAuctionWonEvent(auction, product);
        } else {
            previousStatus = auction.getStatus();
            auction.markFailed();
            log.info("경매 상태 전이 - auctionId: {}, trigger: {}, previousStatus: {}, currentStatus: {}",
                    auction.getId(), trigger, previousStatus, auction.getStatus());
            auctionOutboxService.saveAuctionFailedEvent(auction, product);
        }

        cancelEndCheckJobAfterCommit(auction);

        return AuctionCloseResponse.from(auction);
    }

    private Product getExistingProduct(UUID productId) {
        return productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    private Map<UUID, Product> getProductMap(Page<Auction> auctions) {
        List<UUID> productIds = auctions.getContent().stream()
                .map(Auction::getProductId)
                .distinct()
                .toList();

        if (productIds.isEmpty()) {
            return Map.of();
        }

        return productRepository.findAllByIdInAndDeletedAtIsNull(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private void validateProductOwnerOrMaster(Product product, UUID userId, String userRole) {
        if (isMaster(userRole)) {
            return;
        }

        if (!product.getSellerId().equals(userId)) {
            throw new AuctionException(AuctionErrorCode.AUCTION_FORBIDDEN);
        }
    }

    private void validateStartAt(LocalDateTime startAt) {
        if (!startAt.isAfter(LocalDateTime.now())) {
            throw new AuctionException(AuctionErrorCode.INVALID_AUCTION_PERIOD);
        }
    }

    private void validateDuplicateAuction(UUID productId) {
        boolean hasActiveAuction = auctionRepository.existsByProductIdAndStatusInAndDeletedAtIsNull(
                productId,
                List.of(AuctionStatus.READY, AuctionStatus.PROGRESS)
        );
        if (hasActiveAuction) {
            throw new AuctionException(AuctionErrorCode.DUPLICATE_AUCTION);
        }
    }

    private boolean isMaster(String userRole) {
        return "MASTER".equalsIgnoreCase(userRole) || "ROLE_MASTER".equalsIgnoreCase(userRole);
    }

    private Auction getExistingAuction(UUID auctionId) {
        return auctionRepository.findByIdAndDeletedAtIsNull(auctionId)
                .orElseThrow(() -> new AuctionException(AuctionErrorCode.AUCTION_NOT_FOUND));
    }

    private Page<Auction> findAuctions(Pageable pageable, AuctionStatus status) {
        if (status == null) {
            return auctionRepository.findAllByDeletedAtIsNull(pageable);
        }

        return auctionRepository.findAllByStatusAndDeletedAtIsNull(status, pageable);
    }

    private void validateUpdateRequest(AuctionUpdateRequest request) {
        if (request.startPrice() == null && request.bidUnit() == null && request.startAt() == null) {
            throw new AuctionException(AuctionErrorCode.INVALID_AUCTION_REQUEST);
        }

        if (request.startAt() != null) {
            validateStartAt(request.startAt());
        }
    }

    private void scheduleStartJobAfterCommit(Auction auction) {
        transactionAfterCommitExecutor.execute(() -> auctionScheduleManager.scheduleStartJob(
                auction.getId(),
                auction.getStartAt(),
                () -> auctionSchedulerJobService.startAuction(auction.getId())
        ));
    }

    private void cancelStartJobAfterCommit(Auction auction) {
        transactionAfterCommitExecutor.execute(() -> auctionScheduleManager.cancelStartJob(auction.getId()));
    }

    private void cancelEndCheckJobAfterCommit(Auction auction) {
        transactionAfterCommitExecutor.execute(() -> auctionScheduleManager.cancelEndCheckJob(auction.getId()));
    }





    private boolean isAlreadyClosed(Auction auction) {
        return auction.getStatus() == AuctionStatus.WON || auction.getStatus() == AuctionStatus.FAIL;
    }

    private void validateCloseRequest(Auction auction, UUID winnerId, Integer finalPrice) {
        if (winnerId == null && finalPrice == null) {
            return;
        }

        if (winnerId == null || finalPrice == null) {
            throw new AuctionException(AuctionErrorCode.INVALID_AUCTION_RESULT);
        }

        if (finalPrice < auction.getStartPrice()) {
            throw new AuctionException(AuctionErrorCode.INVALID_AUCTION_RESULT);
        }
    }

    private void validateAuctionOwnerOrMasterOrManager(Auction auction, UUID userId, String userRole) {
        if (isMaster(userRole) || isManager(userRole)) {
            return;
        }

        if (!auction.getSellerId().equals(userId)) {
            throw new AuctionException(AuctionErrorCode.AUCTION_FORBIDDEN);
        }
    }

    private boolean isManager(String userRole) {
        return "MANAGER".equalsIgnoreCase(userRole) || "ROLE_MANAGER".equalsIgnoreCase(userRole);
    }

    @Transactional(readOnly = true)
    public AuctionDepositInfoResponse getAuctionDepositInfo(UUID auctionId) {
        Auction auction = getExistingAuction(auctionId);
        Product product = getExistingProduct(auction.getProductId());

        return new AuctionDepositInfoResponse(
                auction.getId(),
                auction.getStartPrice(),
                product.getName(),
                auction.getEndAt()
        );
    }
}
