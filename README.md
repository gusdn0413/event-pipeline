# Event Pipeline

API 호출 로그를 수집·저장·분석하는 이벤트 파이프라인

---

## 실행 방법

### 1. 필요한 도구
- Docker
- Docker Compose

### 2. 도구 설치 명령어
```powershell
winget install Docker.DockerDesktop
```

### 3. 실행 명령어
```powershell
git clone https://github.com/gusdn0413/event-pipeline
cd event-pipeline
docker-compose up -d --build
```

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
*   **생태계 호환**: JDBC/MyBatis 등 기존 도구를 그대로 사용 (PostgreSQL 확장)

### 2. 데이터 구조 (Schema)

원본 JSON을 통째로 저장하지 않고 분석에 자주 쓰는 메타데이터만 컬럼으로 분리
*   **쿼리 효율**: JSON 파싱 없이 컬럼 인덱스·필터·집계를 그대로 사용
*   **압축 최적화**: `event_type` 같은 분석 차원을 컬럼으로 노출해 TimescaleDB `segmentby`에 활용
*   **하이퍼테이블 요건**: 파티션 키인 `call_at`을 PRIMARY KEY 일부로 포함

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

---

## 구현하면서 고민한 점

### 1. 트래픽 분포를 실제 운영에 가깝게
4개 outcome(SUCCESS / CLIENT_ERROR / SERVER_ERROR / SLOW)을 단순 랜덤으로 뽑으면 25%씩 균등하게 나와 실제 서비스 분포와 거리감이 있음
*   `application.yml`의 weights로 **70/15/5/10** 비율을 설정해 성공이 압도적이도록 조정
*   5xx 안에서도 균등이 아니라 `List.of(500, 500, 502, 503)`로 500을 50%로 가중 (실제 운영의 흔한 5xx 대부분이 DB 장애·타임아웃 등으로 500에 몰린다는 점 반영)

### 2. 메시지 브로커 흐름을 흉내낸 Pub/Sub
실제 운영이라면 NATS/Kafka 같은 브로커가 들어갈 자리지만, 과제 단계이기에 Spring `ApplicationEvent`로 대체
*   provider(simulator)와 consumer(listener)를 **수평 분리**해 서로 직접 의존하지 않도록 구성
*   `@Async` 리스너로 발행/구독 스레드를 나눠 발행자가 구독자의 처리 시간에 영향받지 않도록 함

### 3. 장애 전파 방지 (Circuit Breaker)
NATS/Kafka 등 메세지 브로커 장애 시 의미 없는 publish를 계속 던질 수 있음
*   Resilience4j로 publish 호출을 감싸 **5회 연속 실패 → OPEN(10초) → HALF_OPEN → 성공 시 CLOSED 복귀**
*   OPEN 상태에선 호출이 차단돼 장애 중 무의미한 시도가 쌓이지 않음

### 4. 확장성 고려 — 사전 집계 뷰 (Continuous Aggregate)
데이터가 누적되면 24시간 윈도우 쿼리도 비용이 커지는데, TimescaleDB의 Continuous Aggregate로 하루 단위 사전 집계 뷰를 만들어두면 대시보드 응답이 훨씬 빨라짐
*   다만 효과 검증을 위해선 데이터를 장시간 누적해야 하는데 시뮬레이터를 그만큼 켜두기 어려워 적용 보류