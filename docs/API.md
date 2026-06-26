# API 명세서

산지직경 서비스의 REST API 명세서입니다.

## 공통 사항

### 게이트웨이

모든 외부 요청은 게이트웨이를 통해 들어옵니다.

- 기본 주소: `http://localhost:8000`

### 인증

로그인 후 발급받은 JWT 토큰을 `Authorization` 헤더에 담아 보냅니다.

```
Authorization: Bearer {JWT_TOKEN}
```

인증이 필요한 API는 헤더에 토큰이 없으면 `401 Unauthorized`가 반환됩니다.

### 사용자 역할 (UserRole)

| 역할 | 설명 |
|------|------|
| `MASTER` | 최고 관리자 |
| `MANAGER` | 운영 관리자 |
| `SELLER` | 판매자 |
| `BUYER` | 구매자 |

### 공통 응답 형식

```json
{
  "message": "처리 결과 메시지",
  "data": { ... }
}
```

페이지네이션 응답:

```json
{
  "message": "OK",
  "data": {
    "content": [ ... ],
    "page": 0,
    "size": 10,
    "totalElements": 100,
    "totalPages": 10
  }
}
```

---

## 1. 사용자 서비스 (User Service)

### 1.1 일반 회원가입

```
POST /api/v1/auth/signup
```

**권한**: 없음 (누구나 가능)

**요청 본문**

| 필드 | 타입 | 필수 | 설명 | 검증 |
|------|------|------|------|------|
| `username` | string | O | 아이디 | 4~12자, 소문자+숫자만 |
| `name` | string | O | 이름 | 최대 20자 |
| `email` | string | O | 이메일 | 이메일 형식 |
| `phone` | string | X | 전화번호 | `01X-XXX(X)-XXXX` 형식 |
| `password` | string | O | 비밀번호 | 8~15자, 대소문자+숫자+특수문자 포함 |
| `businessNumber` | string | O | 사업자등록번호 | `XXX-XX-XXXXX` 형식 |
| `slackId` | string | O | Slack ID | |
| `notificationAllow` | boolean | O | 알림 수신 동의 여부 | |
| `role` | string | O | 역할 | `SELLER`, `BUYER` |

```json
{
  "username": "seller01",
  "name": "홍길동",
  "email": "hong@example.com",
  "phone": "010-1234-5678",
  "password": "Password1!",
  "businessNumber": "123-45-67890",
  "slackId": "U12345678",
  "notificationAllow": true,
  "role": "SELLER"
}
```

**응답**: `201 Created`

---

### 1.2 관리자 회원가입

```
POST /api/v1/auth/admin/signup
```

**권한**: 없음 (관리자 키 필요)

**요청 본문**: 일반 회원가입과 동일하나 `businessNumber` 대신 `adminKey` 필드 사용

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `username` | string | O | 아이디 |
| `name` | string | O | 이름 |
| `email` | string | O | 이메일 |
| `phone` | string | O | 전화번호 |
| `password` | string | O | 비밀번호 |
| `slackId` | string | O | Slack ID |
| `notificationAllow` | boolean | O | 알림 수신 동의 여부 |
| `role` | string | O | 역할 (`MASTER`, `MANAGER`) |
| `adminKey` | string | O | 관리자 등록 키 |

**응답**: `201 Created`

---

### 1.3 로그인

```
POST /api/v1/auth/login
```

**권한**: 없음

**요청 본문**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `username` | string | O | 아이디 |
| `password` | string | O | 비밀번호 |

```json
{
  "username": "seller01",
  "password": "Password1!"
}
```

**응답**: `200 OK` - JWT 토큰 포함

---

### 1.4 내 프로필 조회

```
GET /api/v1/users/me
```

**권한**: 로그인 사용자

**응답**: `200 OK`

---

### 1.5 내 프로필 수정

```
PATCH /api/v1/users/me/profile
```

**권한**: 로그인 사용자

**요청 본문**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `name` | string | X | 이름 |
| `phone` | string | X | 전화번호 |
| `slackId` | string | X | Slack ID |

```json
{
  "name": "홍길동",
  "phone": "010-9999-8888",
  "slackId": "U99999999"
}
```

**응답**: `200 OK`

---

### 1.6 사업자번호 수정

```
PATCH /api/v1/users/me/business
```

**권한**: 로그인 사용자

**요청 본문**

| 필드 | 타입 | 필수 | 설명 | 검증 |
|------|------|------|------|------|
| `businessNumber` | string | O | 사업자등록번호 | `XXX-XX-XXXXX` 형식 |

```json
{
  "businessNumber": "987-65-43210"
}
```

**응답**: `200 OK`

---

### 1.7 회원 탈퇴

```
DELETE /api/v1/users/me
```

**권한**: 로그인 사용자

**응답**: `200 OK`

---

### 1.8 유저 단건 조회 (관리자)

```
GET /api/v1/user/one?userId={userId}
```

**권한**: `MASTER`, `MANAGER`

**쿼리 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `userId` | UUID | O | 조회할 유저 ID |

**응답**: `200 OK`

---

### 1.9 유저 목록 조회 (관리자)

```
GET /api/v1/users/all
```

**권한**: `MASTER`, `MANAGER`

**쿼리 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `role` | string | X | 역할 필터 (`MASTER`, `MANAGER`, `SELLER`, `BUYER`) |
| `status` | string | X | 상태 필터 (`ACTIVE`, `SUSPENDED`, `DELETED`) |
| `page` | int | X | 페이지 번호 (기본값: 0) |
| `size` | int | X | 페이지 크기 (기본값: 20) |

**응답**: `200 OK`

---

### 1.10 계정 정지 (관리자)

```
PATCH /api/v1/users/suspended
```

**권한**: `MASTER`

**요청 본문**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `userId` | UUID | O | 정지할 유저 ID |

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**응답**: `200 OK`

---

### 1.11 계정 정지 해제 (관리자)

```
PATCH /api/v1/users/unsuspended
```

**권한**: `MASTER`

**요청 본문**: 계정 정지와 동일

**응답**: `200 OK`

---

## 2. 상품 서비스 (Product Service)

auction-service 내부에 포함됩니다.

### 2.1 상품 등록

```
POST /api/v1/products
```

**권한**: `SELLER`, `MASTER`

**요청 본문**

| 필드 | 타입 | 필수 | 설명 | 검증 |
|------|------|------|------|------|
| `name` | string | O | 상품명 | 최대 100자 |
| `description` | string | O | 상품 설명 | 최대 500자 |
| `quantity` | string | O | 수량 | 최대 50자 |

```json
{
  "name": "고려청자 매병",
  "description": "12세기 고려시대 청자 매병, 상태 양호",
  "quantity": "1점"
}
```

**응답**: `201 Created`

---

### 2.2 상품 단건 조회

```
GET /api/v1/products/{productId}
```

**권한**: 없음 (누구나 가능)

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `productId` | UUID | 상품 ID |

**응답**: `200 OK`

---

### 2.3 상품 목록 조회

```
GET /api/v1/products
```

**권한**: 없음

**쿼리 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `page` | int | X | 페이지 번호 (기본값: 0) |
| `size` | int | X | 페이지 크기 (기본값: 20) |

**응답**: `200 OK`

---

### 2.4 상품 수정

```
PATCH /api/v1/products/{productId}
```

**권한**: `SELLER`(본인), `MANAGER`, `MASTER`

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `productId` | UUID | 상품 ID |

**요청 본문**: 수정할 필드만 포함 (모두 선택)

| 필드 | 타입 | 필수 | 설명 | 검증 |
|------|------|------|------|------|
| `name` | string | X | 상품명 | 최대 100자 |
| `description` | string | X | 상품 설명 | 최대 500자 |
| `quantity` | string | X | 수량 | 최대 50자 |

**응답**: `200 OK`

---

### 2.5 상품 삭제

```
DELETE /api/v1/products/{productId}
```

**권한**: `SELLER`(본인), `MANAGER`, `MASTER`

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `productId` | UUID | 상품 ID |

**응답**: `200 OK` (소프트 삭제)

---

## 3. 경매 서비스 (Auction Service)

### 3.1 경매 등록

```
POST /api/v1/auctions
```

**권한**: `SELLER`, `MASTER`

**요청 본문**

| 필드 | 타입 | 필수 | 설명 | 검증 |
|------|------|------|------|------|
| `productId` | UUID | O | 상품 ID | |
| `startPrice` | int | O | 시작가 | 1 이상 |
| `bidUnit` | int | O | 입찰 단위 | 1 이상 |
| `startAt` | string | O | 경매 시작 시각 | ISO 8601 형식 (`yyyy-MM-ddTHH:mm:ss`) |

```json
{
  "productId": "550e8400-e29b-41d4-a716-446655440000",
  "startPrice": 1000000,
  "bidUnit": 10000,
  "startAt": "2026-07-01T10:00:00"
}
```

**응답**: `201 Created`

---

### 3.2 경매 단건 조회

```
GET /api/v1/auctions/{auctionId}
```

**권한**: 없음

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `auctionId` | UUID | 경매 ID |

**응답**: `200 OK` - 경매 상세 정보 및 실시간 최고가 포함

---

### 3.3 경매 목록 조회

```
GET /api/v1/auctions
```

**권한**: 없음

**쿼리 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `status` | string | X | 경매 상태 필터 (`READY`, `PROGRESS`, `RESULT_PENDING`, `WON`, `SUCCESS`, `FAIL`, `CANCELLED`) |
| `page` | int | X | 페이지 번호 (기본값: 0) |
| `size` | int | X | 페이지 크기 (기본값: 20) |

**경매 상태 설명**

| 상태 | 설명 |
|------|------|
| `READY` | 시작 대기 |
| `PROGRESS` | 진행 중 |
| `RESULT_PENDING` | 결과 처리 중 |
| `WON` | 낙찰 |
| `SUCCESS` | 거래 성사 |
| `FAIL` | 유찰 |
| `CANCELLED` | 취소됨 |

**응답**: `200 OK`

---

### 3.4 경매 수정

```
PATCH /api/v1/auctions/{auctionId}
```

**권한**: `SELLER`(본인), `MANAGER`, `MASTER`

> `READY` 상태인 경매만 수정 가능합니다.

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `auctionId` | UUID | 경매 ID |

**요청 본문**: 수정할 필드만 포함 (모두 선택)

| 필드 | 타입 | 필수 | 설명 | 검증 |
|------|------|------|------|------|
| `startPrice` | int | X | 시작가 | 1 이상 |
| `bidUnit` | int | X | 입찰 단위 | 1 이상 |
| `startAt` | string | X | 시작 시각 | 현재 시각 이후 |

**응답**: `200 OK`

---

### 3.5 경매 취소

```
POST /api/v1/auctions/{auctionId}/cancel
```

**권한**: `SELLER`(본인), `MANAGER`, `MASTER`

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `auctionId` | UUID | 경매 ID |

**요청 본문**

| 필드 | 타입 | 필수 | 설명 | 검증 |
|------|------|------|------|------|
| `reason` | string | O | 취소 사유 | 최대 100자 |

```json
{
  "reason": "상품 상태 이상으로 인한 취소"
}
```

**응답**: `200 OK`

---

### 3.6 경매 수동 시작 (관리자)

```
POST /api/v1/auctions/{auctionId}/start
```

**권한**: `MASTER`, `MANAGER`

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `auctionId` | UUID | 경매 ID |

**응답**: `200 OK` - 경매가 `PROGRESS` 상태로 전환

---

### 3.7 경매 수동 마감 (관리자)

```
POST /api/v1/auctions/{auctionId}/close
```

**권한**: `MASTER`, `MANAGER`

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `auctionId` | UUID | 경매 ID |

**요청 본문** (선택, 생략 시 유찰 처리)

| 필드 | 타입 | 필수 | 설명 | 검증 |
|------|------|------|------|------|
| `winnerId` | UUID | X | 낙찰자 ID | 낙찰 처리 시 필요 |
| `finalPrice` | int | X | 최종 낙찰가 | 1 이상 |

```json
{
  "winnerId": "550e8400-e29b-41d4-a716-446655440001",
  "finalPrice": 1500000
}
```

**응답**: `200 OK` - 낙찰자 있으면 `WON`, 없으면 `FAIL` 상태로 전환

---

## 4. 입찰 서비스 (Bid Service)

입찰은 REST API가 아니라 **WebSocket(STOMP)** 프로토콜을 사용합니다.

### WebSocket 연결

```
ws://localhost:8000/ws/bid
```

연결 시 JWT 토큰을 STOMP `CONNECT` 헤더에 포함해야 합니다.

### 4.1 입찰 (WebSocket)

** SockJs 연결** : /ws/bid

**발행 주소**: `/app/auction/{auctionId}/bid`

**메시지 본문**

| 필드 | 타입 | 설명 |
|------|------|------|
| `bidPrice` | int | 입찰 금액 |
| `clientSeenPrice` | int | 클라이언트가 보고 있는 현재 최고가 |
| `actionType` | string | `BID` (일반 입찰) 또는 `EXTEND` (연장 입찰) |

```json
{
  "bidPrice": 1100000,
  "clientSeenPrice": 1050000,
  "actionType": "BID"
}
```

**구독 주소**: `/topic/auction/{auctionId}` - 경매 실시간 업데이트 수신

**에러 수신**: `/user/queue/errors` - 입찰 실패 메시지 수신

### 4.2 최고 입찰가 조회

```
GET /api/v1/bids/auctions/{auctionId}/highest
```

**권한**: 없음

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `auctionId` | UUID | 경매 ID |

**응답**: `200 OK`

---

## 5. 주문 서비스 (Order Service)

### 5.1 보증금 주문 생성

```
POST /api/v1/orders/deposit
```

**권한**: 로그인 사용자

경매에 참여하기 전에 보증금 결제 주문을 먼저 생성해야 합니다.

**요청 본문**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `auctionId` | UUID | O | 경매 ID |

```json
{
  "auctionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**응답**: `201 Created`

---

### 5.2 내 보증금 주문 목록 조회

```
GET /api/v1/orders/deposit/me
```

**권한**: 로그인 사용자

**쿼리 파라미터**: Spring Pageable 파라미터 사용 (`page`, `size`, `sort`)

**응답**: `200 OK` - 페이지네이션 목록

---

### 5.3 내 낙찰금 주문 목록 조회

```
GET /api/v1/orders/winning/me
```

**권한**: 로그인 사용자

**쿼리 파라미터**: Spring Pageable 파라미터 사용 (`page`, `size`, `sort`)

**응답**: `200 OK` - 페이지네이션 목록

---

## 6. 결제 서비스 (Payment Service)

Toss Payments를 통한 결제를 처리합니다.

### 6.1 결제 승인

```
POST /api/v1/payments/confirm
```

**권한**: 로그인 사용자

보증금 또는 낙찰 잔금 결제를 승인합니다. 프론트엔드에서 Toss Payments 결제 위젯을 통해 결제한 뒤 받은 정보를 이 API로 전달합니다.

**요청 본문**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `paymentKey` | string | O | Toss에서 발급한 결제 키 |
| `tossOrderId` | string | O | Toss 주문 ID |
| `amount` | int | O | 결제 금액 |

```json
{
  "paymentKey": "tviva20240930190000HdW5P3s9NqBe",
  "tossOrderId": "MC1234567890",
  "amount": 100000
}
```

**응답**: `200 OK`

---

### 6.2 결제 단건 조회

```
GET /api/v1/payments/{paymentId}
```

**권한**: 로그인 사용자 (본인 결제만 조회 가능)

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `paymentId` | UUID | 결제 ID |

**응답**: `200 OK`

---

### 6.3 잔금 재결제

```
POST /api/v1/payments/repay/{orderId}
```

**권한**: 로그인 사용자

낙찰 잔금 결제 실패 후 15분 이내에 다른 카드로 재결제할 때 사용합니다.

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `orderId` | UUID | 주문 ID |

**응답**: `200 OK`

---

## 7. 알림 서비스 (Notification Service)

### 7.1 내 알림 목록 조회

```
GET /api/v1/notifications
```

**권한**: 로그인 사용자

**쿼리 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `page` | int | X | 페이지 번호 (기본값: 0) |
| `size` | int | X | 페이지 크기 (기본값: 10) |

**응답**: `200 OK`

---

### 7.2 알림 단건 조회

```
GET /api/v1/notifications/{notificationId}
```

**권한**: 로그인 사용자 (본인 알림만 조회 가능)

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `notificationId` | UUID | 알림 ID |

**응답**: `200 OK`

---

### 7.3 전체 알림 목록 조회 (관리자)

```
GET /api/v1/admin/notifications
```

**권한**: `MANAGER`, `MASTER`

**쿼리 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `page` | int | X | 페이지 번호 (기본값: 0) |
| `size` | int | X | 페이지 크기 (기본값: 10) |

**응답**: `200 OK`

---

## 8. AI 서비스 (AI Service)

RAG(Retrieval-Augmented Generation) 기반 AI 챗봇 API입니다.

### 8.1 채팅 세션 생성

```
POST /api/v1/ai/sessions
```

**권한**: 로그인 사용자

새로운 대화 세션을 시작합니다.

**응답**: `200 OK` - 세션 ID 포함

---

### 8.2 채팅

```
POST /api/v1/ai/sessions/{sessionId}/chat
```

**권한**: 로그인 사용자 (본인 세션만 사용 가능)

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `sessionId` | UUID | 세션 ID |

**요청 본문**

| 필드 | 타입 | 필수 | 설명 | 검증 |
|------|------|------|------|------|
| `message` | string | O | 질문 메시지 | 최대 2000자 |

```json
{
  "message": "경매 참여 방법을 알려주세요."
}
```

**응답**: `200 OK` - AI 답변 포함

---

### 8.3 채팅 세션 목록 조회

```
GET /api/v1/ai/sessions
```

**권한**: 로그인 사용자

**쿼리 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `page` | int | X | 페이지 번호 (기본값: 0) |
| `size` | int | X | 페이지 크기 (기본값: 10) |

**응답**: `200 OK`

---

### 8.4 채팅 메시지 목록 조회

```
GET /api/v1/ai/sessions/{sessionId}/messages
```

**권한**: 로그인 사용자 (본인 세션만 조회 가능)

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `sessionId` | UUID | 세션 ID |

**응답**: `200 OK` - 전체 메시지 목록 (페이지네이션 없음)

---

### 8.5 채팅 세션 삭제

```
DELETE /api/v1/ai/sessions/{sessionId}
```

**권한**: 로그인 사용자 (본인 세션만 삭제 가능)

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `sessionId` | UUID | 세션 ID |

**응답**: `200 OK`

---

## 9. AI 관리자 API (Admin AI)

### 9.1 문서 등록

```
POST /api/v1/admin/ai/documents
Content-Type: multipart/form-data
```

**권한**: `MASTER`

RAG 챗봇이 참고할 문서를 벡터 저장소에 등록합니다. PDF, Word, Excel, txt 등을 지원합니다.

**요청 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `file` | file | O | 업로드할 문서 파일 |
| `source` | string | O | 문서 식별자 (예: `auction-guide-v1`) |

**응답**: `200 OK`

---

### 9.2 문서 목록 조회

```
GET /api/v1/admin/ai/documents
```

**권한**: `MASTER`

**쿼리 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `page` | int | X | 페이지 번호 (기본값: 0) |
| `size` | int | X | 페이지 크기 (기본값: 10) |

**응답**: `200 OK`

---

### 9.3 문서 삭제

```
DELETE /api/v1/admin/ai/documents/{source}
```

**권한**: `MASTER`

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `source` | string | 문서 식별자 |

**응답**: `200 OK` - 해당 source에 속한 문서 전체 삭제

---

### 9.4 문서 재임베딩

```
PUT /api/v1/admin/ai/documents/{source}
Content-Type: multipart/form-data
```

**권한**: `MASTER`

기존 문서를 삭제하고 새 파일로 다시 등록합니다.

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `source` | string | 문서 식별자 |

**요청 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| `file` | file | O | 새로 업로드할 문서 파일 |

**응답**: `200 OK`

---

## 10. 내부 API (Internal)

서비스 간 직접 통신에 사용하는 API입니다. 외부에서는 접근할 수 없습니다.

### 10.1 경매 보증금 정보 조회

```
GET /internal/auctions/{auctionId}
```

order-service 등 내부 서비스가 보증금 생성 시 경매 정보를 검증하기 위해 사용합니다.

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `auctionId` | UUID | 경매 ID |

**응답**: 보증금액, 상품명, 종료 시각

---

### 10.2 유저 알림 수신 여부 조회

```
GET /internal/v1/users/{userId}/notify-allow
```

notification-service 등 내부 서비스가 알림 발송 전 수신 동의 여부를 확인할 때 사용합니다.

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `userId` | UUID | 유저 ID |

**응답**: 알림 수신 동의 여부

---

### 10.3 유저 기본 정보 조회

```
GET /internal/v1/users/{userId}/user-info
```

내부 서비스가 유저 기본 정보(이름, 이메일 등)를 조회할 때 사용합니다.

**경로 파라미터**

| 파라미터 | 타입 | 설명 |
|----------|------|------|
| `userId` | UUID | 유저 ID |

**응답**: 유저 기본 정보
