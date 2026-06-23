package com.bankrupang.sanjijk.auction.global.util;

import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.MDC;

public final class AuctionLogContext {

    private static final String AUCTION_ID_KEY = "auctionId";

    private AuctionLogContext() {
    }

    public static void runWithAuctionId(UUID auctionId, Runnable runnable) {
        callWithAuctionId(auctionId, () -> {
            runnable.run();
            return null;
        });
    }

    public static <T> T callWithAuctionId(UUID auctionId, Supplier<T> supplier) {
        String previousAuctionId = MDC.get(AUCTION_ID_KEY);
        setAuctionId(auctionId);

        try {
            return supplier.get();
        } finally {
            restoreAuctionId(previousAuctionId);
        }
    }

    private static void setAuctionId(UUID auctionId) {
        if (auctionId == null) {
            MDC.remove(AUCTION_ID_KEY);
            return;
        }

        MDC.put(AUCTION_ID_KEY, auctionId.toString());
    }

    private static void restoreAuctionId(String previousAuctionId) {
        if (previousAuctionId == null) {
            MDC.remove(AUCTION_ID_KEY);
            return;
        }

        MDC.put(AUCTION_ID_KEY, previousAuctionId);
    }
}
