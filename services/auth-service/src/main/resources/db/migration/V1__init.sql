CREATE TABLE users
(
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255),
    username    VARCHAR(255),
    role        VARCHAR(50),
    provider    VARCHAR(50),
    provider_id VARCHAR(255)
);