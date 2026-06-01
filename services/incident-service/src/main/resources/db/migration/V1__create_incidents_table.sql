CREATE TABLE incidents (
    id              VARCHAR(36),
    incident_title  VARCHAR(255)    NOT NULL,
    incident_summary TEXT,
    crs             VARCHAR(50),
    x               VARCHAR(50),
    y               VARCHAR(50),
    incident_type   VARCHAR(50)     NOT NULL,
    incident_status VARCHAR(50)     NOT NULL DEFAULT 'OPEN',
    timestamp       TIMESTAMP       NOT NULL,
    last_updated    TIMESTAMP,
    CONSTRAINT pk_incidents PRIMARY KEY (id)
);