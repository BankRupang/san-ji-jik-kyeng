# 테이블 명세서

산지직경 서비스의 DB 테이블 명세서입니다.

---

## 1. 공통 컬럼 (BaseEntity)

아래 테이블들은 `BaseEntity`를 상속하며, 다음 컬럼을 공통으로 가집니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | UUID | PK, NOT NULL | 시간 기반 UUID (자동 생성) |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 (자동 입력) |
| created_by | UUID | - | 생성한 사용자 ID |
| updated_at | TIMESTAMP | NOT NULL | 최근 수정 시각 (자동 갱신) |
| updated_by | UUID | - | 마지막으로 수정한 사용자 ID |
| deleted_at | TIMESTAMP | - | 소프트 삭제 시각 (NULL이면 활성 상태) |
| deleted_by | UUID | - | 삭제한 사용자 ID |

> `deleted_at IS NULL` 조건으로 활성 데이터만 조회합니다.

---

## 2. 스키마 목록

| 스키마 이름 | 담당 서비스 | 설명 |
|---|---|---|
| user_schema | user-service | 사용자 계정 |
| auction_schema | auction-service | 경매, 상품 |
| bid_schema | (예약) | 입찰 (현재 미사용) |
| order_schema | order-service | 주문 |
| payment_schema | payment-service | 결제 |
| notification_schema | notification-service | 알림 |
| ai_schema | ai-service | AI 채팅 |
| keycloak_schema | Keycloak | 인증 (외부 관리) |
| langfuse_schema | Langfuse | LLM 관측 (외부 관리) |

---

## 3. user_schema

### p_users

사용자 계정 정보를 저장합니다. ID는 Keycloak에서 발급한 UUID를 그대로 사용합니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| *(BaseEntity 공통 컬럼)* | | | |
| username | VARCHAR(20) | NOT NULL, UNIQUE | 로그인 아이디 (변경 불가) |
| name | VARCHAR(20) | NOT NULL | 실명 |
| email | VARCHAR(100) | NOT NULL, UNIQUE | 이메일 주소 (변경 불가) |
| phone | VARCHAR(30) | NOT NULL | 전화번호 |
| business_number | VARCHAR | UNIQUE | 사업자번호 (관리자 계정은 없을 수 있음) |
| slack_id | VARCHAR | NOT NULL | Slack 알림 수신용 ID |
| notification_allow | BOOLEAN | - | 알림 수신 동의 여부 |
| role | VARCHAR | NOT NULL | 권한 (`MASTER` / `MANAGER` / `SELLER` / `BUYER`) |
| status | VARCHAR | - | 계정 상태 (`ACTIVE` / `SUSPENDED` / `DELETED`) |

**상태(status) 설명:**

| 값 | 설명 |
|---|---|
| ACTIVE | 정상 활성 계정 |
| SUSPENDED | 정지된 계정 (로그인 제한) |
| DELETED | 소프트 삭제된 계정 |

---

## 4. auction_schema

### p_products

경매에 올릴 상품 정보를 저장합니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| *(BaseEntity 공통 컬럼)* | | | |
| seller_id | UUID | NOT NULL | 판매자 사용자 ID |
| name | VARCHAR | NOT NULL | 상품명 |
| description | VARCHAR | NOT NULL | 상품 설명 |
| quantity | VARCHAR | NOT NULL | 수량 및 단위 (예: "100kg") |

---

### p_auctions

경매 진행 정보를 저장합니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| *(BaseEntity 공통 컬럼)* | | | |
| product_id | UUID | NOT NULL | 경매 대상 상품 ID |
| seller_id | UUID | NOT NULL | 판매자 사용자 ID |
| status | VARCHAR | NOT NULL | 경매 상태 (아래 표 참고) |
| start_price | INT | NOT NULL | 시작가 |
| winner_id | UUID | - | 낙찰자 사용자 ID (낙찰 전까지 NULL) |
| final_price | INT | - | 최종 낙찰가 (낙찰 전까지 NULL) |
| bid_unit | INT | NOT NULL | 입찰 단위 금액 |
| extension_count | INT | NOT NULL | 경매 종료 시간 연장 횟수 |
| start_at | TIMESTAMP | NOT NULL | 경매 시작 시각 |
| end_at | TIMESTAMP | NOT NULL | 경매 종료 시각 |
| cancel_reason | VARCHAR | - | 취소 사유 (취소 시에만 입력) |

**상태(status) 전이 흐름:**

```
READY -> PROGRESS -> RESULT_PENDING -> WON -> SUCCESS
                                    -> FAIL
              -> CANCELLED (READY 상태에서만 가능)
```

| 값 | 설명 |
|---|---|
| READY | 경매 등록 완료, 시작 대기 |
| PROGRESS | 경매 진행 중 |
| RESULT_PENDING | 경매 종료, 결과 처리 대기 |
| WON | 낙찰자 확정 |
| SUCCESS | 결제 완료, 경매 최종 성공 |
| FAIL | 유찰 (낙찰자 없음 또는 결제 실패) |
| CANCELLED | 경매 취소 |

---

### p_auction_outbox

경매 서비스에서 발생한 도메인 이벤트를 다른 서비스에 안정적으로 전달하기 위한 Outbox 테이블입니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| *(BaseEntity 공통 컬럼)* | | | |
| aggregate_type | VARCHAR(50) | NOT NULL | 이벤트 대상 도메인 (항상 `AUCTION`) |
| aggregate_id | UUID | NOT NULL | 이벤트 대상 경매 ID |
| event_type | VARCHAR(50) | NOT NULL | 이벤트 종류 |
| payload | JSONB | NOT NULL | 이벤트 상세 데이터 (JSON) |
| published | BOOLEAN | NOT NULL | Kafka 발행 완료 여부 |
| published_at | TIMESTAMP | - | Kafka 발행 완료 시각 |

> 인덱스: `(published, created_at)` - 미발행 이벤트를 생성 순서대로 빠르게 조회하기 위함

---

### shedlock

분산 환경에서 스케줄러 작업이 여러 인스턴스에서 동시에 실행되지 않도록 잠금을 관리합니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| name | VARCHAR(64) | PK | 잠금 이름 (스케줄러 작업 식별자) |
| lock_until | TIMESTAMP | NOT NULL | 잠금 유지 만료 시각 |
| locked_at | TIMESTAMP | NOT NULL | 잠금 획득 시각 |
| locked_by | VARCHAR(255) | NOT NULL | 잠금을 획득한 인스턴스 식별자 |

---

## 5. order_schema

### p_orders

사용자의 주문 정보를 저장합니다. 예치금 주문과 낙찰 주문 두 가지 유형이 있습니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| *(BaseEntity 공통 컬럼)* | | | |
| order_number | VARCHAR | NOT NULL, UNIQUE | 주문 번호 (예: `ORD-A1B2C3D4`) |
| user_id | UUID | NOT NULL | 주문자 사용자 ID (변경 불가) |
| seller_id | UUID | - | 판매자 사용자 ID (낙찰 주문에만 존재) |
| user_name | VARCHAR(10) | NOT NULL | 주문자 이름 (스냅샷) |
| slack_id | VARCHAR(30) | NOT NULL | 주문자 Slack ID (알림용) |
| auction_id | UUID | NOT NULL | 관련 경매 ID (변경 불가) |
| auction_title | VARCHAR(30) | NOT NULL | 경매 제목 (스냅샷) |
| order_type | VARCHAR | NOT NULL | 주문 유형 (`DEPOSIT` / `WINNING`, 변경 불가) |
| amount | INT | NOT NULL | 결제 금액 |
| request_memo | VARCHAR(200) | - | 주문자 요청 메모 |
| status | VARCHAR | NOT NULL | 주문 상태 (아래 표 참고) |
| payment_due_at | TIMESTAMP | - | 결제 마감 시각 (낙찰 주문에서 15분 후) |
| penalty_due_at | TIMESTAMP | - | 패널티 결제 마감 시각 (결제 실패 후 15분 후) |
| version | BIGINT | - | 낙관적 잠금용 버전 번호 |

> 유니크 제약: `(user_id, auction_id, order_type)` - 동일 경매에 같은 유형의 주문 중복 방지

**주문 유형(order_type):**

| 값 | 설명 |
|---|---|
| DEPOSIT | 예치금 주문 (경매 입찰 전 보증금) |
| WINNING | 낙찰 주문 (낙찰 후 잔금 결제) |

**상태(status) 전이 흐름:**

```
DEPOSIT:  PENDING -> PAYMENT_SUCCESS -> REFUNDED (유찰 시 환불)
                                     -> FORFEITED (낙찰자 결제 실패 시 몰수)

WINNING:  PENDING -> PAYMENT_SUCCESS -> COMPLETED
                  -> PAYMENT_FAILED -> PENALTY_PENDING -> COMPLETED (재결제 성공)
                                                       -> EXPIRED (15분 초과)
```

| 값 | 설명 |
|---|---|
| PENDING | 주문 생성, 결제 대기 |
| PAYMENT_SUCCESS | 결제 성공 |
| COMPLETED | 주문 최종 완료 |
| REFUNDED | 환불 완료 (유찰된 예치금) |
| FORFEITED | 예치금 몰수 (낙찰자가 결제 실패 시) |
| PAYMENT_FAILED | 낙찰 주문 결제 실패 |
| PENALTY_PENDING | 패널티 결제 대기 |
| EXPIRED | 패널티 결제 기간 만료 |

---

### p_order_outbox

주문 서비스에서 발생한 도메인 이벤트를 안정적으로 전달하기 위한 Outbox 테이블입니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | UUID | PK, NOT NULL | 시간 기반 UUID |
| aggregate_type | VARCHAR(100) | NOT NULL | 이벤트 대상 도메인 |
| aggregate_id | UUID | NOT NULL | 이벤트 대상 엔티티 ID |
| event_type | VARCHAR(100) | NOT NULL | 이벤트 종류 |
| payload | JSONB | NOT NULL | 이벤트 상세 데이터 (JSON) |
| status | VARCHAR(20) | NOT NULL | 발행 상태 (`PENDING` / `PUBLISHED` / `FAILED`) |
| retry_count | INT | NOT NULL | 재시도 횟수 (최대 3회) |
| published_at | TIMESTAMP | - | Kafka 발행 완료 시각 |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |

---

## 6. payment_schema

### p_payments

결제 정보를 저장합니다. Toss Payments API와 연동됩니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| *(BaseEntity 공통 컬럼)* | | | |
| order_id | UUID | NOT NULL | 연관 주문 ID |
| user_id | UUID | NOT NULL | 결제자 사용자 ID |
| seller_id | UUID | - | 판매자 ID (낙찰 결제에만 존재) |
| auction_id | UUID | NOT NULL | 관련 경매 ID |
| auction_title | VARCHAR(30) | NOT NULL | 경매 제목 (스냅샷) |
| toss_order_id | VARCHAR(64) | NOT NULL, UNIQUE | Toss에 전달하는 주문 식별자 |
| payment_key | VARCHAR(200) | UNIQUE | Toss 결제 키 (승인 후 발급) |
| payment_type | VARCHAR | NOT NULL | 결제 유형 (아래 표 참고) |
| status | VARCHAR | NOT NULL | 결제 상태 (아래 표 참고) |
| amount | INT | NOT NULL | 결제 요청 금액 |
| original_amount | INT | - | 원래 결제 금액 (재결제 시 차이 발생할 경우 기록) |
| end_at | TIMESTAMP | - | 경매 종료 시각 (보증금 결제 시 Redis TTL 계산용) |
| card_issuer_code | VARCHAR(2) | - | 카드 발급사 코드 |
| card_number | VARCHAR(20) | - | 카드 번호 (마스킹 처리) |
| card_type | VARCHAR(10) | - | 카드 종류 |
| installment_months | INT | default 0 | 할부 개월 수 (0이면 일시불) |
| failure_code | VARCHAR(100) | - | 결제 실패 코드 |
| failure_message | VARCHAR(510) | - | 결제 실패 메시지 |
| receipt_url | VARCHAR(500) | - | 영수증 URL |
| requested_at | TIMESTAMP | NOT NULL | 결제 요청 시각 |
| approved_at | TIMESTAMP | - | 결제 승인 시각 |
| canceled_at | TIMESTAMP | - | 취소(환불) 처리 시각 |
| cancel_amount | INT | - | 취소 금액 |
| cancel_reason | VARCHAR(500) | - | 취소 사유 |
| version | BIGINT | - | 낙관적 잠금용 버전 번호 |

**결제 유형(payment_type):**

| 값 | 설명 |
|---|---|
| REPAY | 보증금 결제 (경매 입장 시) |
| NORMAL | 낙찰 잔금 결제 |
| WINNING_REPAY | 낙찰 잔금 재결제 (결제 실패 후 재시도) |

**상태(status) 전이 흐름:**

```
READY -> IN_PROGRESS -> DONE
                     -> ABORTED (결제 실패)
      -> EXPIRED (유효 시간 초과)
      -> EXPIRE_FAILED (만료 처리 중 오류)
DONE  -> CANCELED (환불)
```

| 값 | 설명 |
|---|---|
| READY | 결제 생성, 사용자 승인 대기 |
| IN_PROGRESS | 인증 완료, Toss 서버 승인 요청 중 |
| DONE | 결제 승인 완료 |
| ABORTED | 결제 실패 (READY 또는 IN_PROGRESS 상태에서 가능) |
| EXPIRED | 유효 시간 초과로 만료 |
| CANCELED | 환불 완료 |
| EXPIRE_FAILED | 만료 처리 중 오류 (수동 확인 필요) |

---

### p_payment_history

결제 상태 변경 이력을 기록합니다. 감사(Audit) 및 디버깅 용도입니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | UUID | PK, NOT NULL | 시간 기반 UUID |
| payment_id | UUID | - | 연관 결제 ID (NULL 가능 - 결제 생성 전 이력) |
| order_id | UUID | NOT NULL | 연관 주문 ID |
| payment_type | VARCHAR | NOT NULL | 결제 유형 |
| prev_status | VARCHAR | - | 변경 전 상태 (최초 생성 시 NULL) |
| next_status | VARCHAR | NOT NULL | 변경 후 상태 |
| reason | VARCHAR | - | 상태 변경 사유 |
| amount | INT | NOT NULL | 해당 시점의 결제 금액 |
| failure_code | VARCHAR(100) | - | 실패 코드 |
| failure_message | VARCHAR(510) | - | 실패 메시지 |
| created_by | UUID | - | 변경 주체 사용자 ID (Kafka/스케줄러 처리 시 NULL) |
| created_at | TIMESTAMP | NOT NULL | 이력 생성 시각 |

---

### p_outbox (payment)

결제 서비스에서 발생한 도메인 이벤트를 안정적으로 전달하기 위한 Outbox 테이블입니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| id | UUID | PK, NOT NULL | 시간 기반 UUID |
| aggregate_type | VARCHAR(100) | NOT NULL | 이벤트 대상 도메인 |
| aggregate_id | UUID | NOT NULL | 이벤트 대상 엔티티 ID |
| event_type | VARCHAR(100) | NOT NULL | 이벤트 종류 |
| payload | JSONB | NOT NULL | 이벤트 상세 데이터 (JSON) |
| status | VARCHAR | NOT NULL | 발행 상태 (`PENDING` / `IN_PROGRESS` / `PUBLISHED` / `FAILED`) |
| retry_count | INT | NOT NULL, default 0 | 재시도 횟수 (최대 3회) |
| published_at | TIMESTAMP | - | Kafka 발행 완료 시각 |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |

---

## 7. notification_schema

### p_notification_logs

사용자에게 발송된 알림 이력을 저장합니다. Slack을 통해 발송됩니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| *(BaseEntity 공통 컬럼)* | | | |
| user_id | UUID | NOT NULL | 알림 수신 사용자 ID |
| type | VARCHAR | NOT NULL | 알림 유형 (아래 표 참고) |
| title | VARCHAR | NOT NULL | 알림 제목 |
| message | TEXT | NOT NULL | 알림 본문 |
| status | VARCHAR | NOT NULL | 발송 상태 (`PENDING` / `SENT` / `FAILED`) |
| reference_id | UUID | - | 관련 엔티티 ID (예: 경매 ID) |
| reference_type | VARCHAR | - | 관련 엔티티 유형 (예: `AUCTION`) |
| slack_id | VARCHAR | NOT NULL | 발송 대상 Slack ID |
| sent_at | TIMESTAMP | - | 실제 발송 완료 시각 |
| retry_count | INT | NOT NULL, default 0 | 재시도 횟수 (최대 5회) |
| next_retry_at | TIMESTAMP | - | 다음 재시도 예정 시각 (지수 백오프: 1, 2, 4, 8, 16분) |

**알림 유형(type):**

| 값 | 설명 |
|---|---|
| AUCTION_WON | 경매 낙찰 알림 |
| AUCTION_FAILED | 경매 유찰 알림 |
| PAYMENT_COMPLETED | 결제 완료 알림 |
| PAYMENT_FAILED | 결제 실패 알림 |
| DEPOSIT_FORFEITED | 예치금 몰수 알림 |
| REFUND_COMPLETED | 환불 완료 알림 |
| BID_OVERTAKEN | 입찰가 추월 알림 |

---

## 8. ai_schema

### p_chat_sessions

AI 채팅 세션 정보를 저장합니다. 세션이 만료되면 메시지 조회가 불가합니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| *(BaseEntity 공통 컬럼)* | | | |
| user_id | UUID | NOT NULL | 세션을 시작한 사용자 ID |
| status | VARCHAR | NOT NULL | 세션 상태 (`ACTIVE` / `PROCESSING` / `EXPIRED`) |
| expired_at | TIMESTAMP | NOT NULL | 세션 만료 시각 |

**상태(status) 설명:**

| 값 | 설명 |
|---|---|
| ACTIVE | 활성 상태, 메시지 교환 가능 |
| PROCESSING | AI 응답 생성 중 |
| EXPIRED | 만료된 세션 |

---

### p_chat_messages

AI 채팅 메시지를 저장합니다. 사용자 입력과 AI 응답 모두 이 테이블에 기록됩니다.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|---|---|---|---|
| *(BaseEntity 공통 컬럼)* | | | |
| session_id | UUID | NOT NULL | 메시지가 속한 채팅 세션 ID |
| role | VARCHAR | NOT NULL | 발화 주체 (`USER` / `ASSISTANT`) |
| content | TEXT | NOT NULL | 메시지 내용 |
