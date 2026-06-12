CREATE TABLE reports
(
    id           UUID PRIMARY KEY,
    type         VARCHAR(50)  NOT NULL,
    format       VARCHAR(20)  NOT NULL,
    status       VARCHAR(30)  NOT NULL,
    title        VARCHAR(255) NOT NULL,
    content_json TEXT,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_reports_type_created_at
    ON reports (type, created_at DESC);

CREATE INDEX idx_reports_status
    ON reports (status);