# Event Pipeline

API 호출 로그를 수집·저장·분석하는 이벤트 파이프라인입니다.

---

## Step 1. 이벤트 설계

### 이벤트 컨셉 — API 호출 로그

웹 서비스의 API 게이트웨이/액세스 로그를 모사합니다. 클라이언트가 보낸 요청(request)과 서버가 돌려준 응답(response)을 한 쌍으로 보고, 그 호출 건마다 한 행을 남깁니다.

본 프로젝트가 다루는 4개 API:

| API | 설명 |
|---|---|
| `POST /api/auth/login` | 로그인 |
| `GET /api/products/search` | 상품 검색 |
| `POST /api/orders` | 주문 생성 |
| `DELETE /api/orders/{orderId}` | 주문 삭제 |

### 설계 이유

API 호출 로그를 **단순히 성공/실패**로만 나누면 분석에서 얻을 수 있는 정보가 제한적입니다. 실제 운영에서 더 의미 있는 질문은 "**실패했다면 왜?**", 그리고 "**성공했지만 느렸나?**" 쪽이라고 보고, 이벤트 타입을 다음 4가지로 분류했습니다.

| event_type | 의미                                        |
|---|-------------------------------------------|
| `SUCCESS` | 200 정상 응답                                 |
| `CLIENT_ERROR` | 4xx 호출자 측 문제 (잘못된 파라미터 / 권한 없음 / 리소스 없음 등) |
| `SERVER_ERROR` | 5xx 서버 측 장애                               |
| `SLOW` | 200으로 성공했지만 응답시간이 임계치(1000ms)를 초과한 케이스    |

특히 `SLOW`를 별도 카테고리로 둔 이유는 **상태코드만으로는 잡히지 않는 성능 문제**를 추적하기 위해서입니다. 200으로 성공했더라도 3초가 걸렸다면 사용자 경험 측면에선 사실상 실패에 가깝고, 일반적인 5xx 장애와는 다른 원인(슬로우 쿼리, 외부 의존 지연 등)을 갖기 때문에 따로 분류해두는 편이 분석에 직관적이라고 판단했습니다.

---

## Step 2. 로그 저장

### 저장소 선택 이유 — PostgreSQL + TimescaleDB

API 호출 로그는 **시계열(time-series) 데이터**라고 생각합니다. 분석 쿼리 대부분이 `call_at`을 축으로 한 GROUP BY (시간대별 추이, 최근 N분 에러율 등)이고, 한 번 INSERT된 데이터는 거의 UPDATE되지 않습니다. 이런 워크로드에는 일반 RDBMS보다 시계열 특화 저장소가 효율적입니다.

**PostgreSQL 확장인 TimescaleDB를 도입**해서 다음 효과를 얻었습니다:

1. **기존 도구 그대로 사용** — JDBC 드라이버, MyBatis, IntelliJ Database 도구 등 Postgres 생태계 변경 없이 시계열 기능만 추가됩니다.
2. **하이퍼테이블** — 일반 테이블처럼 다루지만 내부적으로 **시간 단위로 자동 분할**되어 시간대별 쿼리가 매우 빠릅니다.
3. **압축/리텐션 정책 SQL 한 줄로 적용** — 별도 운영 도구 없이 DB가 데이터 라이프사이클을 자동으로 관리해줍니다.

대안 검토:
- 풀 시계열 DB (InfluxDB 등) — JDBC/SQL 생태계와 단절, 학습 비용
- Postgres에 직접 파티션 테이블 — 청크 자동화 X, 관리 복잡

→ **Postgres 위에 TimescaleDB 확장**이 가장 균형이 좋다고 판단했습니다.

### 스키마

JSON request/response를 통째로 저장하지 않고 **분석에 필요한 메타데이터만 컬럼으로 분리**했습니다.

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

`call_at` 기준 하이퍼테이블에 다음 정책을 적용했습니다:

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

이 구성으로 **대용량 로그 환경에서 조회 성능과 스토리지 효율을 동시에 확보**합니다:
- 시간대 쿼리는 해당 청크만 스캔합니다 (전체 테이블 스캔 X)
- 오래된 데이터는 압축으로 부피가 줄어듭니다
- 별도 cron 없이 DB가 30일 이상 데이터를 자동으로 정리합니다
