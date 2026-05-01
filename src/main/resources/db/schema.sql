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

-- 하이퍼테이블 + 7일 단위 청크 분할
SELECT create_hypertable('api_logs', 'call_at',
    chunk_time_interval => INTERVAL '7 days',
    if_not_exists => TRUE);

-- 압축 설정 (event_type 기준 segment, call_at 내림차순 정렬)
ALTER TABLE api_logs SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'event_type',
    timescaledb.compress_orderby = 'call_at DESC'
);

-- 7일 이상 청크 자동 압축
SELECT add_compression_policy('api_logs', INTERVAL '7 days', if_not_exists => TRUE);

-- 30일 이상 데이터 자동 삭제 (리텐션)
SELECT add_retention_policy('api_logs', INTERVAL '30 days', if_not_exists => TRUE);

CREATE INDEX IF NOT EXISTS idx_api_logs_event_type ON api_logs (event_type);
CREATE INDEX IF NOT EXISTS idx_api_logs_user_id    ON api_logs (user_id);
CREATE INDEX IF NOT EXISTS idx_api_logs_target_id  ON api_logs (target_id);
CREATE INDEX IF NOT EXISTS idx_api_logs_endpoint   ON api_logs (endpoint);
