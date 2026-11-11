CREATE TABLE push_subscriptions
(
    id         UUID PRIMARY KEY,
    endpoint   VARCHAR(2048) NOT NULL UNIQUE,
    p256dh     VARCHAR(255)  NOT NULL,
    auth       VARCHAR(255)  NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL
);
