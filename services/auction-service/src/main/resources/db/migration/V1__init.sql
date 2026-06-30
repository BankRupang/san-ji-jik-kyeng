CREATE TABLE IF NOT EXISTS p_products
(
    id          UUID         NOT NULL,
    seller_id   UUID         NOT NULL,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(255) NOT NULL,
    quantity    VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    created_by  UUID,
    updated_at  TIMESTAMP    NOT NULL,
    updated_by  UUID,
    deleted_at  TIMESTAMP,
    deleted_by  UUID,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS p_auctions
(
    id              UUID        NOT NULL,
    product_id      UUID        NOT NULL,
    seller_id       UUID        NOT NULL,
    status          VARCHAR(50) NOT NULL,
    start_price     INTEGER     NOT NULL,
    winner_id       UUID,
    final_price     INTEGER,
    bid_unit        INTEGER     NOT NULL,
    extension_count INTEGER     NOT NULL DEFAULT 0,
    start_at        TIMESTAMP   NOT NULL,
    end_at          TIMESTAMP   NOT NULL,
    cancel_reason   VARCHAR(255),
    created_at      TIMESTAMP   NOT NULL,
    created_by      UUID,
    updated_at      TIMESTAMP   NOT NULL,
    updated_by      UUID,
    deleted_at      TIMESTAMP,
    deleted_by      UUID,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS p_auction_outbox
(
    id             UUID        NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id   UUID        NOT NULL,
    event_type     VARCHAR(50) NOT NULL,
    payload        JSONB       NOT NULL,
    published      BOOLEAN     NOT NULL DEFAULT FALSE,
    published_at   TIMESTAMP,
    created_at     TIMESTAMP   NOT NULL,
    created_by     UUID,
    updated_at     TIMESTAMP   NOT NULL,
    updated_by     UUID,
    deleted_at     TIMESTAMP,
    deleted_by     UUID,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_auction_outbox_published_created_at
    ON p_auction_outbox (published, created_at);

-- ShedLock 분산 락 테이블
CREATE TABLE IF NOT EXISTS shedlock
(
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
