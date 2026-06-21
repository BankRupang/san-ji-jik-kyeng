package com.bankrupang.sanjijk.auction.outbox.infrastructure.messaging;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

import com.bankrupang.sanjijk.auction.outbox.domain.entity.AuctionOutbox;
import com.bankrupang.sanjijk.auction.outbox.domain.repository.AuctionOutboxRepository;

@Service
@RequiredArgsConstructor
public class AuctionOutboxRelayTransactionService {

    private final AuctionOutboxRepository auctionOutboxRepository;

    @Transactional(readOnly = true)
    public List<AuctionOutbox> findPendingEvents(int batchSize) {
        return auctionOutboxRepository.findByPublishedFalseOrderByCreatedAtAsc(PageRequest.of(0, batchSize))
                .getContent();
    }

    @Transactional
    public void markPublished(UUID outboxId) {
        auctionOutboxRepository.findById(outboxId)
                .ifPresent(AuctionOutbox::markPublished);
    }
}
