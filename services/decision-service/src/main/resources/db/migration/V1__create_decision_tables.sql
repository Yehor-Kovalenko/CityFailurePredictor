CREATE TABLE decisions
(
    id                   UUID PRIMARY KEY,
    prediction_id        VARCHAR(150)     NOT NULL UNIQUE,
    prediction_batch_id  VARCHAR(150)     NOT NULL,
    household_id         VARCHAR(50)      NOT NULL,
    prediction_timestamp TIMESTAMPTZ      NOT NULL,
    target_timestamp     TIMESTAMPTZ      NOT NULL,
    predicted_kwh        DOUBLE PRECISION NOT NULL,
    confidence           DOUBLE PRECISION,
    lower_bound_kwh      DOUBLE PRECISION,
    upper_bound_kwh      DOUBLE PRECISION,
    risk_score           DOUBLE PRECISION NOT NULL,
    risk_level           VARCHAR(20)      NOT NULL,
    decision_result      VARCHAR(30)      NOT NULL,
    alert_id             VARCHAR(100),
    model_type           VARCHAR(50),
    model_version        VARCHAR(50),
    created_at           TIMESTAMPTZ      NOT NULL
);

CREATE INDEX idx_decisions_batch
    ON decisions (prediction_batch_id);

CREATE INDEX idx_decisions_household_created_at
    ON decisions (household_id, created_at DESC);

CREATE INDEX idx_decisions_risk_level
    ON decisions (risk_level);

CREATE INDEX idx_decisions_created_at
    ON decisions (created_at DESC);