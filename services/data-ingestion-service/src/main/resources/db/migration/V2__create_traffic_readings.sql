CREATE TABLE traffic_readings
(
    event_id          UUID         NOT NULL,
    source            VARCHAR(100) NOT NULL,
    junction_id       VARCHAR(50)  NOT NULL,
    reading_timestamp TIMESTAMPTZ  NOT NULL,
    vehicles          INTEGER      NOT NULL,
    ingested_at       TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (event_id, reading_timestamp)
);

SELECT create_hypertable(
               'traffic_readings',
               'reading_timestamp',
               if_not_exists => TRUE
       );

CREATE INDEX idx_traffic_junction_time
    ON traffic_readings (junction_id, reading_timestamp DESC);
