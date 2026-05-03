# 선택 B. AWS 기초 이해

이벤트 파이프라인을 AWS에서 운영한다고 가정한 구성. 자세한 다이어그램은 [architecture.md](architecture.png) 참고

## 1. 아키텍처 개요 및 구성도

EKS(애플리케이션 영역)와 EC2(데이터 영역)를 분리해 운영 안정성과 기술적 요구사항을 동시에 충족하도록 설계

- **EKS (Stateless 영역)**: Spring Boot App (3 Replicas), NATS, Prometheus, Grafana를 배치해 고가용성과 확장성 확보
- **EC2 (Stateful 영역)**: PostgreSQL + TimescaleDB 자체 호스팅으로 데이터 영속성과 시계열 데이터 최적화 기능 활용
- **Internal ALB**: 보안을 위해 운영 도구(Grafana, Actuator)를 내부망 전용 로드밸런서로 통합 관리
- **관제 축 분리**: 비즈니스 로그(PostgreSQL)는 Grafana 대시보드, 시스템·앱 메트릭은 Prometheus가 별도로 수집

## 2. 주요 서비스 역할 및 선정 이유

| 서비스 | 주요 역할 | 선정 이유 |
|---|---|---|
| **Amazon EKS** | 앱 및 브로커 오케스트레이션 | Rolling Update와 Auto Scaling을 통해 무중단 운영 및 트래픽 대응 자동화 |
| **EC2 (DB 전용)** | 시계열 데이터베이스 호스팅 | TimescaleDB Extension을 100% 활용하기 위한 최선의 선택 (Managed 서비스 제약 해결) |
| **Amazon EBS** | 고성능 영속 스토리지 | DB 인스턴스 재시작 시에도 데이터 유지, 일관된 I/O 성능 제공 |
| **Internal ALB** | 내부 트래픽 라우팅 | Path-based Routing으로 하나의 주소에서 여러 관리 도구에 안전하게 접근 (보안 강화) |
| **AWS Backup** | 인프라 장애 복구 자동화 | EBS 스냅샷 생성을 자동화해 하드웨어 장애 시 신속한 복구 지점(RPO) 확보 |
| **Prometheus (EKS 내부)** | 시스템·앱 메트릭 수집 | app의 `/actuator/prometheus`를 주기적으로 pull. JVM·요청량·NATS 메시지 처리량 등 운영 메트릭을 시계열로 보관해 Grafana로 시각화 |

## 3. 설계 시 가장 고민했던 지점

### 3-1. Managed DB(RDS)를 포기하고 EC2 자체 호스팅을 선택한 이유

본 프로젝트의 핵심 자산은 TimescaleDB 기반의 시계열 분석

- **문제점**: AWS RDS는 공식적으로 timescaledb 익스텐션 미지원
- **해결책**: 기능 제한이 있는 매니지드 서비스 대신 EC2에 직접 구축해 하이퍼테이블(Hypertables), 압축(Compression), 데이터 보관 정책(Retention Policy) 기능을 100% 활용하는 방향으로 결정
- **보완책**: 자체 호스팅의 운영 부담은 AWS Backup으로 EBS 스냅샷 자동화해 보완

### 3-2. 백업 전략의 단순화 — 왜 S3 장기 백업을 제외 이유

설계 초기에는 **S3 + pg_dump** 구성을 검토했으나, 본 파이프라인의 특성을 고려해 과감히 제외

- **데이터 정책과의 정렬**: 본 시스템은 30일 경과 데이터를 자동 삭제하는 정책 보유. 따라서 수개월 이상의 장기 보관을 지원하는 S3 백업은 설계 낭비로 판단
- **결론**: '장기 보관'보다 '인프라 장애 시 빠른 복구'가 우선순위라고 판단하여 EBS 스냅샷 기반의 AWS Backup에만 집중

### 3-3. EKS 내 NATS와 Grafana 배치 결정

- **이유**: NATS는 이벤트 전달의 통로, Grafana는 시각화 도구. 두 서비스 모두 영속적인 데이터 저장이 핵심이 아니거나(NATS), 설정값(PVC)만 유지되면 충분해 인프라 유연성이 높은 EKS 내부에 배치하여 관리 포인트를 앱과 통합

### 3-4. 관제 축 분리 — Prometheus 도입

기존 시각화는 PostgreSQL의 비즈니스 로그(API 호출 패턴·에러율 등)에 한정

- **PostgreSQL → Grafana**: 비즈니스 로그 분석 (무엇이 호출됐는가)
- **Prometheus → Grafana**: 시스템·앱 메트릭 (앱이 얼마나 잘 도는가)

두 축을 분리해 Grafana 한 화면에서 통합 관제. Prometheus는 EKS 안에 Pod로 배치해 매니페스트 일관성 유지
