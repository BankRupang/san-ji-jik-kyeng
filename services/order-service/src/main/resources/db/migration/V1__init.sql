CREATE TABLE IF NOT EXISTS p_orders
(
    id             UUID         NOT NULL,
    order_number   VARCHAR(255) NOT NULL,
    user_id        UUID         NOT NULL,
    seller_id      UUID,
    user_name      VARCHAR(10)  NOT NULL,
    slack_id       VARCHAR(30)  NOT NULL,
    auction_id     UUID         NOT NULL,
    auction_title  VARCHAR(30)  NOT NULL,
    order_type     VARCHAR(50)  NOT NULL,
    amount         INTEGER      NOT NULL,
    request_memo   VARCHAR(200),
    status         VARCHAR(50)  NOT NULL,
    payment_due_at TIMESTAMP,
    penalty_due_at TIMESTAMP,
    version        BIGINT,
    created_at     TIMESTAMP    NOT NULL,
    created_by     UUID,
    updated_at     TIMESTAMP    NOT NULL,
    updated_by     UUID,
    deleted_at     TIMESTAMP,
    deleted_by     UUID,
    PRIMARY KEY (id),
    CONSTRAINT uq_orders_order_number UNIQUE (order_number),
    CONSTRAINT uq_orders_user_auction_type UNIQUE (user_id, auction_id, order_type)
);

CREATE TABLE IF NOT EXISTS p_order_outbox
(
    id             UUID         NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    retry_count    INTEGER      NOT NULL DEFAULT 0,
    published_at   TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);
