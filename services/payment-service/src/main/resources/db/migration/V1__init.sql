CREATE TABLE IF NOT EXISTS p_payments
(
    id                  UUID         NOT NULL,
    order_id            UUID         NOT NULL,
    user_id             UUID         NOT NULL,
    seller_id           UUID,
    auction_id          UUID         NOT NULL,
    auction_title       VARCHAR(30)  NOT NULL,
    toss_order_id       VARCHAR(64)  NOT NULL,
    payment_key         VARCHAR(200),
    payment_type        VARCHAR(50)  NOT NULL,
    status              VARCHAR(50)  NOT NULL,
    amount              INTEGER      NOT NULL,
    original_amount     INTEGER,
    end_at              TIMESTAMP,
    card_issuer_code    VARCHAR(2),
    card_number         VARCHAR(20),
    card_type           VARCHAR(10),
    installment_months  INTEGER      DEFAULT 0,
    failure_code        VARCHAR(100),
    failure_message     VARCHAR(510),
    receipt_url         VARCHAR(500),
    requested_at        TIMESTAMP    NOT NULL,
    approved_at         TIMESTAMP,
    canceled_at         TIMESTAMP,
    cancel_amount       INTEGER      NOT NULL DEFAULT 0,
    cancel_reason       VARCHAR(500),
    version             BIGINT,
    created_at          TIMESTAMP    NOT NULL,
    created_by          UUID,
    updated_at          TIMESTAMP    NOT NULL,
    updated_by          UUID,
    deleted_at          TIMESTAMP,
    deleted_by          UUID,
    PRIMARY KEY (id),
    CONSTRAINT uq_payments_toss_order_id UNIQUE (toss_order_id),
    CONSTRAINT uq_payments_payment_key UNIQUE (payment_key)
);

CREATE TABLE IF NOT EXISTS p_payment_history
(
    id              UUID        NOT NULL,
    payment_id      UUID,
    order_id        UUID        NOT NULL,
    payment_type    VARCHAR(50) NOT NULL,
    prev_status     VARCHAR(50),
    next_status     VARCHAR(50) NOT NULL,
    reason          VARCHAR(255),
    amount          INTEGER     NOT NULL,
    failure_code    VARCHAR(100),
    failure_message VARCHAR(510),
    created_by      UUID,
    created_at      TIMESTAMP   NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS p_outbox
(
    id             UUID         NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(50)  NOT NULL,
    retry_count    INTEGER      NOT NULL DEFAULT 0,
    published_at   TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);
