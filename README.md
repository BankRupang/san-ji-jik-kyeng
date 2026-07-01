# 산지직경

> 생산자와 구매자를 실시간으로 연결하여 유통 마진을 줄이고 신선한 농수산물을 공정한 가격에 거래할 수 있는 B2B 경매 플랫폼입니다.

## 핵심 기능

**🔨 실시간 경매**
- WebSocket/STOMP 기반 실시간 입찰
- Redisson 분산 락으로 동시성 제어
- Anti-Sniping (마감 30초 내 입찰 시 1분 연장)

**💳 보증금 기반 결제**
- 방 입장 시 보증금 선결제
- 토스페이먼츠 연동
- 낙찰 실패 시 전액 환불

**🔔 실시간 알림**
- Kafka 이벤트 기반 Slack 알림
- 낙찰 / 결제 / 추월 알림

**🤖 AI 챗봇**
- RAG 기반 경매 규정 안내
- Spring AI + Gemini
- 멀티턴 대화 지원

## 아키텍처 다이어그램

<details>
<summary>접기/펼치기</summary>
<img width="1560" height="1498" alt="image" src="https://github.com/user-attachments/assets/4bbb98a6-bd64-48db-84c0-d919d7c0e6e3" />

</details>

---

## 접속 주소

| 서비스 | 주소 | 계정 |
| --- | --- | --- |
| Kafka-UI | http://localhost:8080 | - |
| Prometheus | http://localhost:9090| - |
| Grafana | http://localhost:3000 | admin / admin |
| Langfuse | http://localhost:3001 | 최초 접속 시 회원가입 |
| Keycloak | http://localhost:18080/ |  |

---

## Docker 실행 방법 (Dev)

### 사전 준비

**1. 환경변수 파일 생성**

`.env.example`을 복사해서 `.env` 파일을 만들고 값을 채웁니다.

**Postgres**

| 변수 | 설명 |
|---|---|
| `POSTGRES_DB` | PostgreSQL 데이터베이스 이름 |
| `POSTGRES_DB_URL` | PostgreSQL JDBC URL (예: `jdbc:postgresql://localhost:5432/postgres`) |
| `POSTGRES_USER` | PostgreSQL 사용자 이름 |
| `POSTGRES_PASSWORD` | PostgreSQL 비밀번호 |

**Keycloak**

| 변수 | 설명 |
|---|---|
| `KEYCLOAK_SERVER_URL` | Keycloak 서버 주소 (예: `http://keycloak:18080`) |
| `KEYCLOAK_REALM` | Keycloak realm 이름 |
| `KEYCLOAK_CLIENT_ID` | Keycloak 클라이언트 ID |
| `KEYCLOAK_CLIENT_SECRET` | Keycloak 클라이언트 시크릿 |
| `KEYCLOAK_ADMIN` | Keycloak 관리자 계정 이름 |
| `KEYCLOAK_ADMIN_PASSWORD` | Keycloak 관리자 비밀번호 |
| `KEYCLOAK_ISSUER_URI` | Keycloak 토큰 발급 URI (예: `http://keycloak:18080/realms/{realm}`) |
| `KC_DB` | Keycloak 연결 DB 종류 (예: `postgres`) |
| `KC_DB_URL` | Keycloak DB JDBC URL |
| `KC_DB_USERNAME` | Keycloak DB 사용자 이름 |
| `KC_DB_PASSWORD` | Keycloak DB 비밀번호 |
| `KC_HOSTNAME_STRICT` | Keycloak 호스트명 엄격 검사 여부 (`false` 권장) |
| `KC_HTTP_ENABLED` | Keycloak HTTP 활성화 여부 (`true` 권장) |

**외부 SaaS**

| 변수 | 설명 |
|---|---|
| `TOSS_CLIENT_KEY` | 토스페이먼츠 클라이언트 키 |
| `TOSS_SECRET_KEY` | 토스페이먼츠 시크릿 키 |
| `SLACK_WEBHOOK_URL` | Slack Webhook URL |
| `SLACK_BOT_TOKEN` | Slack Bot Token |
| `FALLBACK_SLACK_ID` | 알림 실패 시 폴백으로 DM을 보낼 Slack 유저 ID |
| `FALLBACK_NOTIFICATION_ALLOW` | 폴백 알림 허용 여부 (`true` / `false`) |
| `GEMINI_API_KEY` | Google Gemini API 키 |

**시크릿 키**

| 변수 | 설명 |
|---|---|
| `MASTER_KEY` | 마스터 계정 등록 시크릿 키 |
| `MANAGER_KEY` | 매니저 계정 등록 시크릿 키 |

**Langfuse**

| 변수 | 설명 |
|---|---|
| `LANGFUSE_NEXTAUTH_SECRET` | Langfuse 세션 암호화 키 (`openssl rand -base64 32`) |
| `LANGFUSE_SALT` | Langfuse 해시 솔트 (`openssl rand -base64 32`) |
| `LANGFUSE_AUTH_HEADER` | Langfuse OTLP 인증 헤더 |

- `LANGFUSE_AUTH_HEADER`: Langfuse UI에서 프로젝트 생성 후 발급된 키를 주입해야 합니다. (`echo -n "pk-lf-xxx:sk-lf-xxx" \| base64`)

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
| langfuse | 3001 |
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
