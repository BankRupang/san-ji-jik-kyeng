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

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.repository.AuctionRepository;
import com.bankrupang.sanjijk.auction.auction.domain.type.AuctionStatus;
import com.bankrupang.sanjijk.auction.auction.exception.AuctionErrorCode;
import com.bankrupang.sanjijk.auction.auction.exception.AuctionException;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.request.AuctionCreateRequest;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionCreateResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionDetailResponse;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionListResponse;
import com.bankrupang.sanjijk.auction.product.domain.entity.Product;
import com.bankrupang.sanjijk.auction.product.domain.repository.ProductRepository;
import com.bankrupang.sanjijk.auction.product.exception.ProductErrorCode;
import com.bankrupang.sanjijk.auction.product.exception.ProductException;
import com.bankrupang.sanjijk.common.response.PageResponse;
import com.bankrupang.sanjijk.common.util.PageableUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final ProductRepository productRepository;

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

        Page<AuctionListResponse> response = new PageImpl<>(content, pageable, auctions.getTotalElements());

        return PageResponse.of(response);
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

}
