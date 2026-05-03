# 선택 A. Kubernetes 기초 이해

이벤트 파이프라인을 Kubernetes 클러스터에 배포한다고 가정하고 작성한 매니페스트.
운영 시나리오를 가정해 stateless 워크로드(app·NATS·Grafana)만 K8s에 배치하고, **DB는 K8s 밖 외부 호스트에서 자체 운영** (선택 B의 EC2 + TimescaleDB와 동일 컨셉)

## 1. 매니페스트 구성

| 파일 | 포함 리소스 | 대상 |
|---|---|---|
| [`configmap.yaml`](configmap.yaml) | ConfigMap | 앱 환경 설정 (NATS_URL, 외부 DB 주소 등) |
| [`secret.yaml`](secret.yaml) | Secret | DB password |
| [`nats.yaml`](nats.yaml) | Deployment + Service | NATS 브로커 |
| [`app.yaml`](app.yaml) | Deployment + Service | 이벤트 생성기 앱 (replicas 3) |
| [`grafana.yaml`](grafana.yaml) | Deployment + Service | Grafana 시각화 대시보드 |
| [`grafana-pvc.yaml`](grafana-pvc.yaml) | PersistentVolumeClaim | Grafana 대시보드·사용자 설정 영구 저장 |

## 2. 매니페스트별 역할 및 선택 이유

### `configmap.yaml`
*   **역할**: 앱이 사용할 환경변수(NATS 주소, 외부 DB 주소, 브로커 모드 등)를 모아둔 ConfigMap
*   **선택 이유**: 환경별(개발, 스테이징, 운영)로 바뀌는 설정값을 코드와 분리. ConfigMap만 다른 값으로 갈아끼우면 코드를 다시 빌드하지 않아도 환경 전환이 가능

### `secret.yaml`
*   **역할**: DB 비밀번호를 담은 Secret
*   **선택 이유**: 민감 정보를 ConfigMap과 분리해서 별도 접근 권한을 걸 수 있음. 비밀번호가 로그나 명령어 결과에 그대로 노출되는 것 방지

### `nats.yaml` (Deployment + Service)
*   **역할**: NATS 브로커 컨테이너 1대, 다른 컴포넌트가 접근할 수 있는 Service
*   **선택 이유**:
    *   **Deployment**: NATS는 메시지를 메모리에서만 처리해서 데이터를 보관할 디스크가 필요 없음. 그래서 일반 Deployment로 충분 (나중에 디스크 영속화가 필요해지면 StatefulSet으로 변경)
    *   **Service**: 앱이 nats-service:4222 라는 이름으로 안정적으로 접속할 수 있도록 통로 역할

### `app.yaml` (Deployment + Service)
*   **역할**: 이벤트 생성기 앱 컨테이너 3대, 클러스터 내부에서 접근할 Service
*   **선택 이유**:
    *   **Deployment**: 앱은 상태가 없어 여러 대 띄워도 똑같이 동작. 3대로 트래픽 분산과 가용성 확보
    *   **RollingUpdate**: 새 버전 배포 시 한 대씩 교체해 다운타임 없이 진행
    *   **ConfigMap·Secret 주입**: 일반 설정은 ConfigMap 통째로, 비밀번호는 Secret에서 키 하나만 환경변수로 주입
    *   **actuator 헬스체크**: Kubernetes가 컨테이너 상태 확인해 자동 재시작·트래픽 차단
    *   **Service**: 클러스터 내부에서 앱에 안정적으로 접근할 수 있는 통로

### `grafana.yaml` + `grafana-pvc.yaml` (Deployment + Service + PVC)
*   **역할**: Grafana 컨테이너 1대, 외부 접근용 LoadBalancer Service, 대시보드 설정 저장용 영구 디스크(PVC)
*   **선택 이유**:
    *   **Deployment (replicas 1)**: PVC가 ReadWriteOnce라 1대만 운영 가능. 시각화는 1대로 충분
    *   **PVC**: 대시보드 설정·사용자 정보·플러그인 등을 저장. 컨테이너 재시작 시에도 설정 유지
    *   **LoadBalancer Service**: 사용자가 외부에서 대시보드에 접속해야 하므로 외부 노출 (EKS면 ALB로 자동 매핑)
