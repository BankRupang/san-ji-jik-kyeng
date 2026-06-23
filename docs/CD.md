# CD 파이프라인

## 전체 흐름

`main` 브랜치에 코드가 merge되면 두 워크플로우가 동시에 자동 실행됩니다.

```
main 브랜치에 merge
        │
        ├─── [deploy-ecs.yml] 자동 실행
        │          JAR 빌드 -> Docker 이미지 빌드 -> ECR push
        │          -> ECS 롤링 배포 -> 안정화 대기
        │
        └─── [deploy-ec2.yml] 자동 실행 (모니터링 EC2만)
                   SSM으로 EC2에 명령 전송
                   -> git fetch & reset -> .env 구성 -> docker compose up
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

**3. ECS 롤링 배포 & 안정화 대기**

task definition은 건드리지 않고, `--force-new-deployment`로 ECS가 `:latest` 이미지를 다시 pull하게 만듭니다.
배포 명령을 모두 보낸 뒤, 각 서비스의 안정화 대기를 백그라운드로 동시에 실행합니다. 하나라도 실패하면 즉시 워크플로우가 실패합니다.

```
force-new-deployment 10개 서비스 순차 전송
           ↓
안정화 대기 10개 서비스 병렬 실행 (O(1) 지연)
           ↓
모두 통과해야 워크플로우 성공
```

> `aws ecs wait services-stable`은 한 번에 최대 10개까지만 받습니다. 서비스를 1개씩 넘기되 백그라운드(`&`)로 동시에 실행해 대기 시간이 서비스 수에 비례해 늘어나는 것을 방지합니다.

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

Kafka EC2는 수동 실행에서만 선택할 수 있습니다.

### 동작 방식

GitHub Actions 러너에서 AWS SSM Run Command로 EC2에 셸 스크립트를 전송합니다. EC2에 직접 SSH로 들어가지 않습니다.

EC2에서 실행되는 내용은 다음과 같습니다.

```
1. 레포가 없으면 git clone, 있으면 git fetch & reset --hard
2. AWS SSM Parameter Store에서 환경변수 값 조회
3. .env 파일 생성 (권한 600)
4. docker compose pull & up -d
```

### Kafka EC2 환경변수

| 변수 | SSM 파라미터 경로 |
|---|---|
| `KAFKA_PRIVATE_IP` | IMDS(EC2 메타데이터)에서 직접 조회 |
| `KAFKA_CLUSTER_ID` | `/sanji/kafka/cluster-id` |

`KAFKA_PRIVATE_IP`는 Kafka가 자기 자신의 IP를 광고해야 하기 때문에 EC2 메타데이터 서버(IMDSv2)에서 직접 읽습니다.

### 모니터링 EC2 환경변수

| 변수 | SSM 파라미터 경로 |
|---|---|
| `KAFKA_PRIVATE_IP` | `/sanji/kafka/private-ip` |
| `GRAFANA_ADMIN_PASSWORD` | `/sanji/monitoring/grafana-admin-password` |

환경변수를 채운 뒤 `envsubst '$KAFKA_PRIVATE_IP'`로 Prometheus 설정 템플릿(`prometheus.prod.yml.template`)을 실제 설정 파일로 변환합니다. 치환 대상을 `$KAFKA_PRIVATE_IP`로 명시해 PromQL의 `$job`, `$__interval` 같은 표현이 빈 값으로 바뀌는 것을 방지합니다.

### 배포 완료 확인

SSM 명령을 전송한 뒤 결과가 나올 때까지 기다립니다. `Success`가 아니면 워크플로우가 실패합니다. 표준 출력과 표준 에러 로그를 GitHub Actions 로그에 출력해 디버깅할 수 있습니다.
