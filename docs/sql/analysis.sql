-- 분석 쿼리 (최근 24시간)


-- 1. 5xx 에러율
SELECT
    NOW() AS time,
    ROUND(100.0 * COUNT(*) FILTER (WHERE status_code >= 500)
                / NULLIF(COUNT(*), 0), 2) AS server_error_rate
FROM api_logs
WHERE call_at >= NOW() - INTERVAL '24 hours';


-- 2. 4xx 에러율
SELECT
    NOW() AS time,
    ROUND(100.0 * COUNT(*) FILTER (WHERE status_code >= 400 AND status_code < 500)
                / NULLIF(COUNT(*), 0), 2) AS client_error_rate
FROM api_logs
WHERE call_at >= NOW() - INTERVAL '24 hours';


-- 3. 분당 트래픽 (TPS, 4xx/5xx 분리)
SELECT
    time_bucket('1 minute', call_at) AS time,
    ROUND(COUNT(*)::numeric / 60, 2)                                                       AS tps,
    ROUND(COUNT(*) FILTER (WHERE status_code >= 400 AND status_code < 500)::numeric / 60, 2) AS client_error_tps,
    ROUND(COUNT(*) FILTER (WHERE status_code >= 500)::numeric / 60, 2)                     AS server_error_tps
FROM api_logs
WHERE call_at >= NOW() - INTERVAL '24 hours'
GROUP BY time
ORDER BY time;


-- 4. 응답시간 P50/P95/P99 (1시간 단위)
SELECT
    time_bucket('1 hour', call_at) AS time,
    ROUND(PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY response_time)::numeric, 0) AS p50,
    ROUND(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY response_time)::numeric, 0) AS p95,
    ROUND(PERCENTILE_CONT(0.99) WITHIN GROUP (ORDER BY response_time)::numeric, 0) AS p99
FROM api_logs
WHERE call_at >= NOW() - INTERVAL '24 hours'
GROUP BY time
ORDER BY time;


-- 5. 느린 API TOP 5 (P95 기준)
SELECT
    endpoint,
    ROUND(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY response_time)::numeric, 0) AS p95_ms
FROM api_logs
WHERE call_at >= NOW() - INTERVAL '24 hours'
GROUP BY endpoint
ORDER BY p95_ms DESC
LIMIT 5;


-- 6. 에러율 높은 API TOP 5
SELECT
    endpoint,
    ROUND(100.0 * COUNT(*) FILTER (WHERE status_code >= 400)
                / NULLIF(COUNT(*), 0), 2) AS error_rate
FROM api_logs
WHERE call_at >= NOW() - INTERVAL '24 hours'
GROUP BY endpoint
ORDER BY error_rate DESC
LIMIT 5;
