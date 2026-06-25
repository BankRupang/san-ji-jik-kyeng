# CD 파이프라인

## 전체 흐름

`main` 브랜치에 코드가 merge되면 두 워크플로우가 동시에 자동 실행됩니다.

```
main 브랜치에 merge
        │
        ├─── [deploy-ecs.yml] 자동 실행
        │          JAR 빌드 -> Docker 이미지 빌드 -> ECR push
        │          -> Wave 1(인프라) -> Wave 2(앱1) -> Wave 3(앱2) -> Wave 4(ai)
        │
        └─── [deploy-ec2.yml] 자동 실행 (모니터링 EC2만)
                   필요한 파일을 S3에 업로드
                   -> SSM으로 EC2에 명령 전송
                   -> S3 sync -> .env 구성 -> docker compose up
```

Kafka EC2는 재기동 시 입찰 이벤트가 끊기기 때문에 자동 배포 대상에서 제외합니다. 경매가 없는 시간에 담당자가 GitHub Actions에서 수동으로 실행합니다.

---

## AWS 인증 방식

Access Key를 GitHub Secret에 저장하지 않습니다. GitHub Actions가 발급한 단기 OIDC 토큰으로 AWS에 로그인합니다.
IAM에 역할이 미리 정의되어 있으며, 신뢰 조건은 `repo:BankRupang/san-ji-jik-kyeng:*`에서 온 토큰만 허용합니다.

GitHub 저장소 변수 `AWS_ROLE_ARN`에 해당 역할 ARN을 등록해두어야 합니다.

---

## deploy-ecs.yml (ECS 배포)

### 트리거

| 조건 | 동작 |
|---|---|
| `main` 브랜치 push | 자동 실행 |
| GitHub Actions 수동 실행 | 재배포가 필요할 때 버튼으로 실행 |

### 단계별 설명

**1. JAR 빌드**

Gradle 캐시를 복원한 뒤 `./gradlew bootJar -x test --parallel`로 전체 서비스 JAR를 빌드합니다. `-x test`는 CI에서 이미 테스트를 통과한 코드만 merge되기 때문에 생략합니다.

**2. Docker 이미지 빌드 & ECR push**

서비스 10개를 순서대로 빌드해서 ECR에 `:latest` 태그로 올립니다. 모든 Dockerfile은 빌드 컨텍스트가 레포 루트입니다.

| 서비스 | Dockerfile 위치 |
|---|---|
| config-server | `config-server/Dockerfile` |
| discovery-server | `discovery-server/Dockerfile` |
| gateway-server | `gateway-server/Dockerfile` |
| user-service | `services/user-service/Dockerfile` |
| auction-service | `services/auction-service/Dockerfile` |
| bid-service | `services/bid-service/Dockerfile` |
| order-service | `services/order-service/Dockerfile` |
| payment-service | `services/payment-service/Dockerfile` |
| notification-service | `services/notification-service/Dockerfile` |
| ai-service | `services/ai-service/Dockerfile` |

**3. Wave 배포**

task definition은 건드리지 않고, `--force-new-deployment`로 ECS가 `:latest` 이미지를 다시 pull하게 만듭니다.
10개 서비스를 한꺼번에 롤링하지 않고 wave 단위로 나눠 순서대로 배포합니다.

**wave로 나누는 이유**: 계정의 Fargate On-Demand vCPU 한도가 30이고, 정상 운영 시 19.5 vCPU를 사용합니다.
롤링 배포 중에는 기존(old) 태스크와 신규(new) 태스크가 잠깐 함께 뜨기 때문에, 10개를 동시에 롤링하면 최대 ~39 vCPU가 필요해 한도를 초과해서 배포를 실패합니다.
wave로 나누면 한 wave 안에서만 겹치므로 최대 27.5 vCPU로 유지됩니다.

**wave 구성**

| Wave | 서비스 |
|---|---|
| Wave 1 (인프라) | config-server, discovery-server, gateway-server |
| Wave 2 (앱1) | user-service, auction-service, order-service, payment-service |
| Wave 3 (앱2) | bid-service, notification-service |
| Wave 4 (AI) | ai-service |

```
build job: JAR 빌드 -> 이미지 빌드 -> ECR push
      ↓
Wave 1: config / discovery / gateway 동시 배포 + 완료 대기
      ↓
Wave 2: user / auction / order / payment 동시 배포 + 완료 대기
      ↓
Wave 3: bid / notification 동시 배포 + 완료 대기
      ↓
Wave 4: ai-service 단독 배포 + 완료 대기 (최대 30분)
```

각 wave 안에서는 서비스마다 `update-service`와 `aws ecs wait services-stable`을 하나의 백그라운드 작업으로 묶어 동시에 실행합니다. `aws ecs wait services-stable`은 ECS 서비스가 배포 완료(안정 상태)가 될 때까지 주기적으로 상태를 확인하는 AWS CLI 명령입니다. wave 안의 모든 서비스가 완료되어야 다음 wave로 넘어갑니다. 하나라도 실패하면 워크플로우가 실패합니다.

### 롤백

`:latest` 방식은 즉시 롤백이 불가합니다.

- **일반 복구**: 직전 정상 커밋으로 되돌리는 PR을 `main`에 merge -> CD가 자동 재배포
- **긴급 복구**: ECR에 남아 있는 이전 이미지를 `:latest`로 다시 태깅 후 push -> ECS force-new-deployment

---

## deploy-ec2.yml (EC2 배포)

### 트리거

| 조건 | 대상 | 동작 |
|---|---|---|
| `main` 브랜치 push | 모니터링 EC2 | 자동 실행 |
| GitHub Actions 수동 실행 | kafka / monitoring / both 선택 | 선택한 대상 배포 |

Kafka 배포는 경매 금지 기간(자정)에만 실행한다는 정책에 따라 Kafka EC2는 수동 실행에서만 선택할 수 있습니다.

### 동작 방식

GitHub Actions 러너에서 AWS SSM Run Command로 EC2에 셸 스크립트를 전송합니다. EC2에 직접 SSH로 들어가지 않습니다.

EC2에서 실행되는 내용은 다음과 같습니다.

```
1. S3(sanji-terraform-state/deploy/{kafka|monitoring}/)에서 필요한 파일만 동기화
   - compose 파일, 설정 파일, 배포 스크립트
2. 서버 유형별 스크립트 실행 (scripts/deploy-kafka.sh 또는 scripts/deploy-monitoring.sh)
   - SSM / IMDS / RDS API에서 환경변수 값 조회
   - .env 파일 생성 (권한 600)
   - docker compose pull & up -d
```

S3에 올라가는 파일 목록은 다음과 같습니다.

| 대상 | S3 경로 | 파일 |
|---|---|---|
| Kafka EC2 | `deploy/kafka/` | `docker-compose.kafka.yml`, `jmx/kafka.yml`, `scripts/deploy-kafka.sh`, `scripts/env.sh` |
| 모니터링 EC2 | `deploy/monitoring/` | `docker-compose.monitoring.yml`, `monitoring/` 전체, `scripts/deploy-monitoring.sh`, `scripts/env.sh` |

### Kafka EC2 환경변수

| 변수 | SSM 파라미터 경로 |
|---|---|
| `KAFKA_PRIVATE_IP` | IMDS(EC2 메타데이터)에서 직접 조회 |
| `KAFKA_CLUSTER_ID` | `/sanji/prod/kafka/cluster-id` |

`KAFKA_PRIVATE_IP`는 Kafka가 자기 자신의 IP를 광고해야 하기 때문에 EC2 메타데이터 서버(IMDSv2)에서 직접 읽습니다.

### 모니터링 EC2 환경변수

| 변수 | 조회 방식 |
|---|---|
| `KAFKA_PRIVATE_IP` | SSM `/sanji/prod/kafka/private-ip` |
| `GRAFANA_ADMIN_PASSWORD` | SSM `/sanji/prod/monitoring/grafana-admin-password` |
| `SLACK_WEBHOOK_URL` | SSM `/sanji/prod/monitoring/slack-webhook-url` |
| `LANGFUSE_NEXTAUTH_SECRET` | SSM `/sanji/prod/langfuse/nextauth-secret` |
| `LANGFUSE_SALT` | SSM `/sanji/prod/langfuse/salt` |
| `DB_PASSWORD` | SSM `/sanji/prod/db/password` |
| `RDS_HOST` | RDS API (`describe-db-instances`)에서 직접 조회 |
| `MONITORING_PUBLIC_IP` | IMDS(EC2 메타데이터)에서 직접 조회 |

`LANGFUSE_DATABASE_URL`과 `LANGFUSE_NEXTAUTH_URL`은 위 값들을 조합해서 .env에 씁니다.

환경변수를 채운 뒤 `envsubst '$KAFKA_PRIVATE_IP'`로 Prometheus 설정 템플릿(`prometheus.prod.yml.template`)을 실제 설정 파일로 변환합니다. 치환 대상을 `$KAFKA_PRIVATE_IP`로 명시해 PromQL의 `$job`, `$__interval` 같은 표현이 빈 값으로 바뀌는 것을 방지합니다.

ECS 디스커버리 스크립트(`ecs-discovery.sh`)를 크론에 등록해 5분마다 실행합니다. Prometheus가 Fargate 태스크 IP를 동적으로 파악하기 위한 용도입니다. `ecs-targets.json`이 없으면 빈 파일로 초기화합니다.

`docker compose up -d` 이후 `prometheus` 컨테이너를 한 번 재시작해 새 설정을 반영합니다.

### 배포 완료 확인

SSM 명령을 전송한 뒤 결과가 나올 때까지 기다립니다. `Success`가 아니면 워크플로우가 실패합니다. 표준 출력과 표준 에러 로그를 GitHub Actions 로그에 출력해 디버깅할 수 있습니다.
