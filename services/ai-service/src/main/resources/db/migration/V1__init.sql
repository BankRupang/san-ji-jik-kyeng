CREATE TABLE IF NOT EXISTS p_chat_sessions
(
    id         UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    status     VARCHAR(50) NOT NULL,
    expired_at TIMESTAMP   NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    created_by UUID,
    updated_at TIMESTAMP   NOT NULL,
    updated_by UUID,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS p_chat_messages
(
    id         UUID        NOT NULL,
    session_id UUID        NOT NULL,
    role       VARCHAR(50) NOT NULL,
    content    TEXT        NOT NULL,
    created_at TIMESTAMP   NOT NULL,
    created_by UUID,
    updated_at TIMESTAMP   NOT NULL,
    updated_by UUID,
    deleted_at TIMESTAMP,
    deleted_by UUID,
    PRIMARY KEY (id)
);
