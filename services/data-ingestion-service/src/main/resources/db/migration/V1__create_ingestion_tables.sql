CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE electricity_readings
(
    event_id          UUID             NOT NULL,
    source            VARCHAR(100)     NOT NULL,
    household_id      VARCHAR(50)      NOT NULL,
    tariff_type       VARCHAR(20)      NOT NULL,
    reading_timestamp TIMESTAMPTZ      NOT NULL,
    kwh               DOUBLE PRECISION NOT NULL,
    ingested_at       TIMESTAMPTZ      NOT NULL,
    PRIMARY KEY (event_id, reading_timestamp)
);

SELECT create_hypertable(
               'electricity_readings',
               'reading_timestamp',
               if_not_exists => TRUE
       );

CREATE INDEX idx_electricity_household_time
    ON electricity_readings (household_id, reading_timestamp DESC);

CREATE TABLE dataset_import_state
(
    id                 BIGSERIAL PRIMARY KEY,
    file_name          VARCHAR(255) NOT NULL UNIQUE,
    next_record_number BIGINT       NOT NULL DEFAULT 1,
    completed          BOOLEAN      NOT NULL DEFAULT FALSE,
    imported_rows      BIGINT       NOT NULL DEFAULT 0,
    skipped_rows       BIGINT       NOT NULL DEFAULT 0,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);