# Event Pipeline

API 호출 로그를 수집·저장·분석하는 이벤트 파이프라인

---

## Step 1. 이벤트 생성기

실제 운영 환경의 API Gateway 액세스 로그를 시뮬레이션하여, 이벤트를 생성하는 스크립트

### 1. 설계 의도 및 목적
단순 무작위 생성이 아닌, **실제 서비스에서 발생할 수 있는 트래픽 패턴**을 모사
*   **다양한 API 엔드포인트**: 로그인, 조회, 주문, 삭제 등 상태 변화를 일으키는 핵심 비즈니스 로직 포함
*   **현실적인 응답 분포**: 정상 응답(70%) 외에도 클라이언트 오류, 서버 장애, 타임아웃(Slow API) 상황을 가중치 기반으로 구성

### 2. 이벤트 발생 로직
*   **수행 방식**: `@Scheduled`를 이용해 50ms~1000ms 사이의 랜덤한 간격으로 이벤트를 발행
*   **전달 구조**: `ApplicationEventPublisher`와 `@Async`를 활용한 비동기 Pub-Sub 모델 구현

### 3. 상태 코드 및 가중치 상세
운영 환경의 톤을 맞추기 위해 `application.yml`의 가중치 설정

| 유형 (Outcome) | 비율 | 지연 시간 | 비고 |
| :--- | :---: | :--- | :--- |
| **SUCCESS** | 70% | 10~300ms | HTTP 200 |
| **CLIENT_ERROR** | 15% | 5~100ms | HTTP 400, 401, 403 균등 분포 |
| **SERVER_ERROR** | 5% | 100~2000ms | HTTP 5xx (500 발생 빈도 50% 설정) |
| **SLOW** | 10% | 1000~3000ms | HTTP 200, 응답시간만 1초 이상 |

### 4. 데이터 스키마 (Message Format)
이벤트는 분석의 편의를 위해 규격화된 JSON 형태로 발행
```json
{
  "header": { "token": "tok_user123", "agent": "phone" },
  "endpoint": "/api/products/search",
  "method": "GET",
  "callAt": "2026-05-01T14:23:45.123",
  "responseTime": 87,
  "requestData": { "productId": "3" },
  "responseData": { "statusCode": 200, "errorCode": null, "keyword": "monitor" }
}
```

### 5. API 목록

| API | 설명 |
|---|---|
| POST /api/auth/login | 로그인 |
| GET /api/products/search | 상품 검색 |
| POST /api/orders | 주문 생성 |
| DELETE /api/orders/{orderId} | 주문 삭제 |

---

## Step 2. 로그 저장 (PostgreSQL + TimescaleDB 선택)

### 1. 이유 및 목적
단순 RDBMS 적재가 아닌, **시계열 분석과 운영 자동화에 최적화된 저장소**를 채택
*   **시계열 특화**: 하이퍼테이블 자동 파티셔닝과 시간 범위 쿼리 최적화로 대량 데이터에서도 일정한 성능 유지
*   **운영 자동화**: SQL만으로 데이터 압축 및 보관 주기(Retention) 정책 자동 적용
*   **생태계 호환**: PostgreSQL 확장이라 JDBC/MyBatis 등 기존 도구를 그대로 사용 (시계열 전용 DB로 갈 때의 학습 비용 회피)

### 2. 데이터 구조 (Schema)

원본 JSON을 파싱하여 주요 메타데이터를 컬럼으로 분리 저장
```sql
CREATE TABLE api_logs (
    id                BIGSERIAL    NOT NULL,
    user_id           VARCHAR(64)  NOT NULL,    -- header.token에서 추출
    agent             VARCHAR(20),              -- 기기 유형 (phone, desktop 등)
    target_id         VARCHAR(64),              -- 비즈니스 대상 ID (productId 등)
    event_type        VARCHAR(20)  NOT NULL,    -- SUCCESS, SERVER_ERROR 등
    http_method       VARCHAR(10)  NOT NULL,    -- GET, POST, DELETE 등
    endpoint          VARCHAR(255) NOT NULL,    -- API 경로
    status_code       INT          NOT NULL,
    response_time     INT          NOT NULL,    -- ms 단위
    error_code        VARCHAR(50),              -- 상세 에러 코드
    call_at           TIMESTAMP(0) NOT NULL,    -- 이벤트 발생 시각 (Partition Key)
    creator           VARCHAR(50)  NOT NULL,    -- 생성자 (logConsumer 고정)
    created_at        TIMESTAMP(0) NOT NULL,    -- 적재 시각
    modifier          VARCHAR(50)  NOT NULL,    -- 수정자
    modified_at       TIMESTAMP(0) NOT NULL,    -- 수정 시각
    PRIMARY KEY (id, call_at)
);
```

### 3. 데이터 관리 정책

TimescaleDB 네이티브 기능을 활용해 로그 라이프사이클 자동화

| 정책 | 설정 | 기대 효과 |
|---|---|---|
| **청크 분할** | 7일 단위 | 시간 범위 조회 시 불필요한 데이터 스캔 방지 |
| **압축(Compression)** | 7일 경과 시 | 스토리지 사용량 절감 및 과거 데이터 조회 최적화 |
| **보관(Retention)** | 30일 후 자동 삭제 | 기간 만료 데이터 자동 삭제로 디스크 풀 방지 |

```sql
-- 1. 하이퍼테이블 변환 (시간 기반 파티셔닝)
SELECT create_hypertable('api_logs', 'call_at', chunk_time_interval => INTERVAL '7 days');

-- 2. 압축 정책: event_type별 그룹화로 압축률 향상
ALTER TABLE api_logs SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'event_type'
);
SELECT add_compression_policy('api_logs', INTERVAL '7 days');

-- 3. 리텐션 정책: 30일 경과 데이터 자동 삭제
SELECT add_retention_policy('api_logs', INTERVAL '30 days');
```