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

## 팀원 소개

| 이름 | GitHub | 담당 역할 |
|---|---|---|
| <img src="https://github.com/rlaalsdn0421.png" width="60"><br>김민우 | [@rlaalsdn0421](https://github.com/rlaalsdn0421) | Bid / WebSocket |
| <img src="https://github.com/SuJeKim.png" width="60"><br>김지민 | [@SuJeKim](https://github.com/SuJeKim) | Order / Payment |
| <img src="https://github.com/qkrwns1478.png" width="60"><br>박준식 | [@qkrwns1478](https://github.com/qkrwns1478) | DevOps / Infra |
| <img src="https://github.com/sinyeowon.png" width="60"><br>신여원 | [@sinyeowon](https://github.com/sinyeowon) | Auction / Product |
| <img src="https://github.com/dddd2356.png" width="60"><br>오영현 | [@dddd2356](https://github.com/dddd2356) | 개발리드 / AI / Notification |
| <img src="https://github.com/cork-7.png" width="60"><br>이승민 | [@cork-7](https://github.com/cork-7) | User / Auth |

---

## 서비스 구조

각 서비스가 독립적으로 실행되는 마이크로서비스 구조입니다.

| 서비스 | 포트 | 역할 |
|---|---|---|
| gateway-server | 8000 | 모든 요청의 입구, 라우팅 처리 |
| config-server | 8888 | 각 서비스의 설정을 한 곳에서 관리 |
| discovery-server (Eureka) | 8761 | 서비스 위치 등록 및 탐색 |
| user-service | 19091 | Keycloak 연동, 사용자 인증 및 계정 관리 |
| auction-service | 19092 | 경매 방 생성 및 일정 관리 |
| bid-service | 19093 | 실시간 입찰 처리 (WebSocket, 동시성 제어) |
| order-service | 19094 | 낙찰 후 주문 처리 및 보증금 관리 |
| payment-service | 19095 | 토스페이먼츠 연동 결제 처리 |
| notification-service | 19096 | Kafka 이벤트 수신 후 Slack 알림 발송 |
| ai-service | 19097 | RAG 기반 AI 챗봇 서빙 |

## 아키텍처 다이어그램

<details>
<summary>접기/펼치기</summary>
<img width="1560" height="1498" alt="image" src="https://github.com/user-attachments/assets/4bbb98a6-bd64-48db-84c0-d919d7c0e6e3" />

</details>

---

## 문서

| 문서 | 설명 |
|---|---|
| [API 명세](./docs/API.md) | 전체 API 엔드포인트 및 요청/응답 형식 |
| [테이블 명세](./docs/TABLES.md) | DB 테이블 구조 |
| [인프라 구조](./docs/INFRASTRUCTURE.md) | AWS 인프라 설계 및 네트워크 구성 |
| [CI 파이프라인](./docs/CI.md) | PR 생성 시 자동 빌드 및 테스트 |
| [CD 배포 가이드](./docs/CD.md) | ECS 롤링 배포 + EC2 compose 배포 흐름 |

---

## 기술 스택

![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.15-6DB33F?logo=springboot&logoColor=white)
![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-2025.0.2-6DB33F?logo=spring&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring_AI-1.0.0-6DB33F?logo=spring&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)
![Kafka](https://img.shields.io/badge/Apache_Kafka-3.7.0-231F20?logo=apachekafka&logoColor=white)
![Keycloak](https://img.shields.io/badge/Keycloak-25.0-4D4D4D?logo=keycloak&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?logo=docker&logoColor=white)
![AWS](https://img.shields.io/badge/AWS-FF9900?logo=amazonaws&logoColor=white)
![Terraform](https://img.shields.io/badge/Terraform-1.10+-7B42BC?logo=terraform&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?logo=githubactions&logoColor=white)
![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?logo=prometheus&logoColor=white)
![Grafana](https://img.shields.io/badge/Grafana-F46800?logo=grafana&logoColor=white)
![Langfuse](https://img.shields.io/badge/Langfuse-2-000000?logoColor=white)

---

## 접속 주소 (로컬)

| 서비스 | 주소 | 계정 |
| --- | --- | --- |
| Kafka-UI | http://localhost:8080 | - |
| Prometheus | http://localhost:9090 | - |
| Grafana | http://localhost:3000 | admin / admin |
| Langfuse | http://localhost:3001 | 최초 접속 시 회원가입 |
| Keycloak | http://localhost:18080/ |  |

---

## Docker 실행 방법 (Dev)

### 사전 준비 (Prerequisites)

로컬에서 실행하려면 아래 도구가 설치되어 있어야 합니다.

- Java 21
- Gradle 8.14.5 (프로젝트에 포함된 `gradlew` 사용 시 자동 설치)
- Docker & Docker Compose

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
