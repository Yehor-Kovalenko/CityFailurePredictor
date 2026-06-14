CREATE TABLE notifications
(
    id               UUID PRIMARY KEY,
    alert_id         VARCHAR(255) NOT NULL UNIQUE,
    household_id     VARCHAR(255) NOT NULL,
    title            VARCHAR(255) NOT NULL,
    message          TEXT         NOT NULL,
    severity         VARCHAR(30)  NOT NULL,
    status           VARCHAR(30)  NOT NULL,
    risk_score       DOUBLE PRECISION,
    predicted_kwh    DOUBLE PRECISION,
    target_timestamp TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_notifications_status_created_at
    ON notifications (status, created_at DESC);

CREATE INDEX idx_notifications_household_id
    ON notifications (household_id);

CREATE UNIQUE INDEX idx_notifications_alert_id
    ON notifications (alert_id);