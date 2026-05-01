CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE IF NOT EXISTS api_logs (
    id                BIGSERIAL    NOT NULL,
    user_id           VARCHAR(64)  NOT NULL,
    agent             VARCHAR(20),
    target_id         VARCHAR(64),
    event_type        VARCHAR(20)  NOT NULL,
    http_method       VARCHAR(10)  NOT NULL,
    endpoint          VARCHAR(255) NOT NULL,
    status_code       INT          NOT NULL,
    response_time     INT          NOT NULL,
    error_code        VARCHAR(50),
    call_at           TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id, call_at)
);

SELECT create_hypertable('api_logs', 'call_at', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_api_logs_event_type ON api_logs (event_type);
CREATE INDEX IF NOT EXISTS idx_api_logs_user_id    ON api_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_api_logs_target_id  ON api_logs (target_id);
CREATE INDEX IF NOT EXISTS idx_api_logs_endpoint   ON api_logs (endpoint);
