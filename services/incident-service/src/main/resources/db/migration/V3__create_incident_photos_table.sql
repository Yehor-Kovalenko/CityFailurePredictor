CREATE TABLE incident_photos
(
    incident_id  VARCHAR(36) PRIMARY KEY REFERENCES incidents (id) ON DELETE CASCADE,
    content_type VARCHAR(50) NOT NULL,
    data         BYTEA       NOT NULL,
    created_at   TIMESTAMP   NOT NULL
);
