CREATE TABLE IF NOT EXISTS p_users
(
    id                  UUID         NOT NULL,
    username            VARCHAR(20)  NOT NULL,
    name                VARCHAR(20)  NOT NULL,
    email               VARCHAR(100) NOT NULL,
    phone               VARCHAR(30)  NOT NULL,
    business_number     VARCHAR(255),
    slack_id            VARCHAR(255) NOT NULL,
    notification_allow  BOOLEAN      NOT NULL DEFAULT FALSE,
    role                VARCHAR(50)  NOT NULL,
    status              VARCHAR(50),
    created_at          TIMESTAMP    NOT NULL,
    created_by          UUID,
    updated_at          TIMESTAMP    NOT NULL,
    updated_by          UUID,
    deleted_at          TIMESTAMP,
    deleted_by          UUID,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_business_number UNIQUE (business_number)
);
