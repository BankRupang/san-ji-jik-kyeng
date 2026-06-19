package com.bankrupang.sanjijk.auction.auction.application.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.bankrupang.sanjijk.auction.auction.domain.entity.Auction;
import com.bankrupang.sanjijk.auction.auction.domain.repository.AuctionRepository;
import com.bankrupang.sanjijk.auction.auction.exception.AuctionErrorCode;
import com.bankrupang.sanjijk.auction.auction.exception.AuctionException;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.request.AuctionCreateRequest;
import com.bankrupang.sanjijk.auction.auction.presentation.dto.response.AuctionCreateResponse;
import com.bankrupang.sanjijk.auction.product.domain.entity.Product;
import com.bankrupang.sanjijk.auction.product.domain.repository.ProductRepository;
import com.bankrupang.sanjijk.auction.product.exception.ProductErrorCode;
import com.bankrupang.sanjijk.auction.product.exception.ProductException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuctionService {

    private final AuctionRepository auctionRepository;
    private final ProductRepository productRepository;

    @Transactional
    public AuctionCreateResponse createAuction(UUID userId, AuctionCreateRequest request) {
        Product product = getExistingProduct(request.productId());
        validateProductOwnerOrMaster(product, userId);
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

    private Product getExistingProduct(UUID productId) {
        return productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    private void validateProductOwnerOrMaster(Product product, UUID userId) {
        if (hasRole("ROLE_MASTER")) {
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

    private boolean hasRole(String role) {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            return false;
        }

        return SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities()
                .stream()
                .anyMatch(authority -> authority.getAuthority().equals(role));
    }

}
