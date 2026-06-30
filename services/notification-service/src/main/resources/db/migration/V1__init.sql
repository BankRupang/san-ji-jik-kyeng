CREATE TABLE IF NOT EXISTS p_notification_logs
(
    id             UUID        NOT NULL,
    user_id        UUID        NOT NULL,
    type           VARCHAR(50) NOT NULL,
    title          VARCHAR(255) NOT NULL,
    message        TEXT        NOT NULL,
    status         VARCHAR(50) NOT NULL,
    reference_id   UUID,
    reference_type VARCHAR(255),
    slack_id       VARCHAR(255) NOT NULL,
    sent_at        TIMESTAMP,
    retry_count    INTEGER     NOT NULL DEFAULT 0,
    next_retry_at  TIMESTAMP,
    created_at     TIMESTAMP   NOT NULL,
    created_by     UUID,
    updated_at     TIMESTAMP   NOT NULL,
    updated_by     UUID,
    deleted_at     TIMESTAMP,
    deleted_by     UUID,
    PRIMARY KEY (id)
);
