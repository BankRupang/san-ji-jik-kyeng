# 산지직경

## 접속 주소

| 서비스 | 주소 | 계정 |
| --- | --- | --- |
| Kafka-UI | http://localhost:8080 | - |
| Prometheus | http://localhost:9090| - |
| Grafana | http://localhost:3000 | admin / admin |

---

## Docker 실행 방법 (Dev)

### 사전 준비

**1. 환경변수 파일 생성**

`.env.example`을 복사해서 `.env` 파일을 만들고 값을 채웁니다.

| 변수 | 설명 |
|---|---|
| `POSTGRES_DB` | PostgreSQL 데이터베이스 이름 |
| `POSTGRES_USER` | PostgreSQL 사용자 이름 |
| `POSTGRES_PASSWORD` | PostgreSQL 비밀번호 |
| `KEYCLOAK_SERVER_URL` | Keycloak 서버 주소 (로컬: `http://localhost:18080`) |
| `KEYCLOAK_REALM` | Keycloak realm 이름 |
| `KEYCLOAK_CLIENT_ID` | Keycloak 클라이언트 ID |
| `KEYCLOAK_CLIENT_SECRET` | Keycloak 클라이언트 시크릿 |
| `MANAGER_KEY` | 매니저 계정 등록 시크릿 키 |
| `MASTER_KEY` | 마스터 계정 등록 시크릿 키 |
| `TOSS_CLIENT_KEY` | 토스페이먼츠 클라이언트 키 |
| `TOSS_SECRET_KEY` | 토스페이먼츠 시크릿 키 |
| `SLACK_WEBHOOK_URL` | Slack Webhook URL |
| `SLACK_BOT_TOKEN` | Slack Bot Token |
| `GEMINI_API_KEY` | Google Gemini API 키 |

**2. JAR 빌드**

```bash
./gradlew bootJar -x test
```

**3. Docker 이미지 빌드**

```bash
docker compose build
```

---

### 실행

```bash
docker compose up -d
```

전체 서비스가 백그라운드에서 순서대로 올라옵니다. config-server healthcheck가 통과된 이후에 나머지 서비스들이 기동됩니다.

---

### 상태 확인

```bash
docker compose ps
```

모든 서비스가 `Up` 상태이면 정상입니다.

| 서비스 | 포트 |
|---|---|
| gateway-server | 8000 |
| config-server | 8888 |
| discovery-server (Eureka) | 8761 |
| keycloak | 18080 |
| postgres | 5432 |
| redis | 6379 |
| kafka | 9092 |
| user-service | 19091 |
| auction-service | 19092 |
| bid-service | 19093 |
| order-service | 19094 |
| payment-service | 19095 |
| notification-service | 19096 |
| ai-service | 19097 |

---

### 종료

```bash
docker compose down
```

데이터(postgres, redis, kafka)도 함께 삭제하려면:

```bash
docker compose down -v
```

---

### 재빌드가 필요한 경우

소스 코드를 변경한 경우 JAR과 이미지를 다시 빌드해야 합니다.

```bash
./gradlew bootJar -x test
docker compose build
docker compose up -d
```

특정 서비스만 재빌드하려면:

```bash
./gradlew :{서비스명}:bootJar -x test
docker compose build {서비스명}
docker compose up -d {서비스명}
```

예시:

```bash
./gradlew :order-service:bootJar -x test
docker compose build order-service
docker compose up -d order-service
```
