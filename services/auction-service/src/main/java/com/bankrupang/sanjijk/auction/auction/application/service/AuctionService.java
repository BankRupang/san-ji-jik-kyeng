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
import com.bankrupang.sanjijk.auction.outbox.application.service.AuctionOutboxService;
import com.bankrupang.sanjijk.auction.product.domain.entity.Product;
import com.bankrupang.sanjijk.auction.product.domain.repository.ProductRepository;
import com.bankrupang.sanjijk.auction.product.exception.ProductErrorCode;
import com.bankrupang.sanjijk.auction.product.exception.ProductException;
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

    @Transactional
    public AuctionCreateResponse createAuction(UUID userId, String userRole, AuctionCreateRequest request) {
        Product product = getExistingProduct(request.productId());
        validateProductOwnerOrMaster(product, userId, userRole);
        validateStartAt(request.startAt());

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
        Auction auction = getExistingAuction(auctionId);

        return closeAuction(auction, request);
    }

    @Transactional
    public AuctionCloseResponse closeAuctionByEndedEvent(UUID auctionId, boolean hasBid, UUID winnerId, Long finalPrice) {
        Auction auction = getExistingAuction(auctionId);
        AuctionCloseRequest request = hasBid
                ? new AuctionCloseRequest(winnerId, toFinalPrice(finalPrice))
                : null;

        return closeAuction(auction, request);
    }

    @Transactional
    public void extendAuctionByExtendedEvent(UUID auctionId, LocalDateTime newEndAt) {
        Auction auction = getExistingAuction(auctionId);

        if (auction.getStatus() != AuctionStatus.PROGRESS) {
            log.info("경매 연장 이벤트 처리 생략 - PROGRESS 상태가 아닙니다. auctionId: {}, status: {}",
                    auctionId, auction.getStatus());
            return;
        }

        if (newEndAt == null) {
            throw new AuctionException(AuctionErrorCode.INVALID_AUCTION_PERIOD);
        }

        if (!newEndAt.isAfter(auction.getEndAt())) {
            log.info("경매 연장 이벤트 처리 생략 - 기존 종료 시각 이후가 아닙니다. auctionId: {}, currentEndAt: {}, newEndAt: {}",
                    auctionId, auction.getEndAt(), newEndAt);
            return;
        }

        auction.extendEndAt(newEndAt);
        scheduleEndCheckJobAfterCommit(auction);
    }

    private AuctionCloseResponse closeAuction(Auction auction, AuctionCloseRequest request) {
        if (isAlreadyClosed(auction)) {
            return AuctionCloseResponse.from(auction);
        }

        validateCloseRequest(auction, request);

        auction.markResultPending();
        Product product = getExistingProduct(auction.getProductId());

        if (hasWinningResult(request)) {
            auction.markWon(request.winnerId(), request.finalPrice());
            auctionOutboxService.saveAuctionWonEvent(auction, product);
        } else {
            auction.markFailed();
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

    private void scheduleEndCheckJobAfterCommit(Auction auction) {
        transactionAfterCommitExecutor.execute(() -> auctionScheduleManager.scheduleEndCheckJob(
                auction.getId(),
                auction.getEndAt(),
                () -> auctionSchedulerJobService.checkAuctionEnd(auction.getId())
        ));
    }

    private Integer toFinalPrice(Long finalPrice) {
        if (finalPrice == null) {
            return null;
        }

        try {
            return Math.toIntExact(finalPrice);
        } catch (ArithmeticException e) {
            throw new AuctionException(AuctionErrorCode.INVALID_AUCTION_RESULT);
        }
    }

    private boolean isAlreadyClosed(Auction auction) {
        return auction.getStatus() == AuctionStatus.WON || auction.getStatus() == AuctionStatus.FAIL;
    }

    private void validateCloseRequest(Auction auction, AuctionCloseRequest request) {
        if (request == null) {
            return;
        }

        if (isInvalidWinningResultPair(request)) {
            throw new AuctionException(AuctionErrorCode.INVALID_AUCTION_RESULT);
        }

        if (!hasWinningResult(request)) {
            return;
        }

        if (request.finalPrice() < auction.getStartPrice()) {
            throw new AuctionException(AuctionErrorCode.INVALID_AUCTION_RESULT);
        }
    }

    private boolean hasWinningResult(AuctionCloseRequest request) {
        return request != null && request.winnerId() != null && request.finalPrice() != null;
    }

    private boolean isInvalidWinningResultPair(AuctionCloseRequest request) {
        return (request.winnerId() == null && request.finalPrice() != null)
                || (request.winnerId() != null && request.finalPrice() == null);
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

}
