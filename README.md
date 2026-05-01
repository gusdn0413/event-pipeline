# Event Pipeline

API 호출 로그를 수집·저장·분석하는 이벤트 파이프라인.

---

## Step 1. 이벤트 설계

### 이벤트 컨셉 — API 호출 로그

웹 서비스의 API 게이트웨이/액세스 로그를 모사. 클라이언트 요청(request)과 서버 응답(response)을 한 쌍으로 보고, 호출 건마다 한 행 저장.

본 프로젝트가 다루는 4개 API:

| API | 설명 |
|---|---|
| `POST /api/auth/login` | 로그인 |
| `GET /api/products/search` | 상품 검색 |
| `POST /api/orders` | 주문 생성 |
| `DELETE /api/orders/{orderId}` | 주문 삭제 |

### 설계 이유

API 호출 로그를 **단순히 성공/실패**로만 나누면 분석에서 얻을 수 있는 정보가 제한적. 실제 운영에서 더 의미 있는 질문은 "**실패했다면 왜?**", "**성공했지만 느렸나?**" 쪽 → 이벤트 타입을 4가지로 분류.

| event_type | 의미                                        |
|---|-------------------------------------------|
| `SUCCESS` | 200 정상 응답                                 |
| `CLIENT_ERROR` | 4xx 호출자 측 문제 (잘못된 파라미터 / 권한 없음 / 리소스 없음 등) |
| `SERVER_ERROR` | 5xx 서버 측 장애                               |
| `SLOW` | 200으로 성공했지만 응답시간이 임계치(1000ms)를 초과한 케이스    |

`SLOW`를 별도 카테고리로 둔 이유는 **상태코드만으로는 잡히지 않는 성능 문제** 추적. 200으로 성공했어도 응답시간이 길면 슬로우 쿼리/외부 의존 지연 같은 별개 원인 → 5xx와 분리해서 추적.

---

## Step 2. 로그 저장

### 저장소 채택 사유 — PostgreSQL + TimescaleDB

API 호출 로그는 시계열 데이터. 분석 쿼리 대부분이 `call_at` 축의 GROUP BY(시간대별 추이, 최근 N분 에러율 등). INSERT 후 UPDATE 거의 없는 워크로드.

→ **PostgreSQL 확장 TimescaleDB 채택**.

도입 효과:

1. **기존 도구 그대로 사용** — JDBC, MyBatis, IntelliJ Database 등 Postgres 생태계 변경 없이 시계열 기능 추가
2. **하이퍼테이블** — 시간 단위 자동 분할. 시간대별 쿼리는 해당 청크만 스캔
3. **압축/리텐션 정책 SQL 한 줄** — 별도 운영 도구 없이 DB가 데이터 라이프사이클 관리

대안 검토:
- 풀 시계열 DB (InfluxDB 등) — JDBC/SQL 생태계와 단절, 학습 비용
- Postgres에 직접 파티션 테이블 — 청크 자동화 X, 관리 복잡

→ Postgres에 시계열 기능만 얹는 형태로 위 단점 회피.

### 스키마

JSON request/response 통째 저장 X. 분석에 필요한 **메타데이터만 컬럼 분리**.

```sql
CREATE TABLE api_logs (
    id                BIGSERIAL    NOT NULL,
    user_id           VARCHAR(64)  NOT NULL,    -- 호출자 (header.token에서 디코딩)
    agent             VARCHAR(20),              -- phone | desktop
    target_id         VARCHAR(64),              -- requestData.productId (있을 때만)
    event_type        VARCHAR(20)  NOT NULL,    -- SUCCESS / CLIENT_ERROR / SERVER_ERROR / SLOW
    http_method       VARCHAR(10)  NOT NULL,    -- GET / POST / DELETE
    endpoint          VARCHAR(255) NOT NULL,    -- /api/orders/{orderId} 같은 template path
    status_code       INT          NOT NULL,
    response_time     INT          NOT NULL,    -- ms 단위
    error_code        VARCHAR(50),              -- 4xx/5xx 시 의미 코드 (NOT_FOUND, INTERNAL_ERROR 등)
    call_at           TIMESTAMP(3) NOT NULL,    -- 호출 발생 시각
    PRIMARY KEY (id, call_at)                   -- 하이퍼테이블 요건상 파티션 키(call_at) 포함
);
```

### 시계열 정책

`call_at` 기준 하이퍼테이블에 적용한 정책:

| 정책 | 설정 | 효과 |
|---|---|---|
| **청크 분할** | 7일 단위 | 일주일 단위로 데이터 자동 파티셔닝 |
| **압축** | 7일 경과 후 자동 | 오래된 청크 압축 — 스토리지 절약 |
| **리텐션** | 30일 후 자동 삭제 | 무한 누적 방지 |

```sql
SELECT create_hypertable('api_logs', 'call_at',
    chunk_time_interval => INTERVAL '7 days');

ALTER TABLE api_logs SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'event_type'
);

SELECT add_compression_policy('api_logs', INTERVAL '7 days');
SELECT add_retention_policy('api_logs', INTERVAL '30 days');
```

적용 효과:
- 시간대 쿼리: 해당 청크만 스캔 (전체 테이블 스캔 X)
- 오래된 데이터: 압축으로 부피 ↓
- 30일 이상 데이터: DB가 자동 정리 (별도 cron X)
