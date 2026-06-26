#!/bin/bash
# 테스트 계정 생성 + testseller로 사과 상품 생성 스크립트
# 실행 전에 gateway-server, user-service, auction-service가 켜져 있어야 합니다.
# 사용법: bash scripts/create-test-users.sh

set -e

# .env 파일에서 환경 변수 읽기
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/../.env"

if [ ! -f "$ENV_FILE" ]; then
  echo "[오류] .env 파일이 없습니다: $ENV_FILE"
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

if [ -z "$MANAGER_KEY" ] || [ -z "$MASTER_KEY" ]; then
  echo "[오류] .env에 MANAGER_KEY 또는 MASTER_KEY가 설정되어 있지 않습니다."
  exit 1
fi

if [ -z "$FALLBACK_SLACK_ID" ]; then
  echo "[오류] .env에 FALLBACK_SLACK_ID가 설정되어 있지 않습니다."
  exit 1
fi

BASE_URL="${BASE_URL:-http://localhost:8000}"

# 결과 출력 함수
print_result() {
  local label="$1"
  local http_code="$2"
  local body="$3"

  if [ "$http_code" = "201" ]; then
    echo "[성공] $label (HTTP $http_code)"
  elif [ "$http_code" = "409" ]; then
    echo "[스킵] $label - 이미 존재하는 계정입니다 (HTTP $http_code)"
  else
    echo "[실패] $label (HTTP $http_code)"
    echo "  응답: $body"
  fi
}

echo "테스트 계정 생성을 시작합니다. (BASE_URL=$BASE_URL)"
echo "---"

# 1. Buyer
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/auth/signup" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"buyer99\",
    \"name\": \"테스트 구매자\",
    \"email\": \"buyer99@test.com\",
    \"phone\": \"010-1234-0001\",
    \"password\": \"Test1234!\",
    \"businessNumber\": \"123-45-00001\",
    \"slackId\": \"$FALLBACK_SLACK_ID\",
    \"notificationAllow\": false,
    \"role\": \"BUYER\"
  }")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
print_result "Buyer (buyer99)" "$HTTP_CODE" "$BODY"

# 2. Seller
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/auth/signup" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"seller99\",
    \"name\": \"테스트 판매자\",
    \"email\": \"seller99@test.com\",
    \"phone\": \"010-1234-0002\",
    \"password\": \"Test1234!\",
    \"businessNumber\": \"123-45-00002\",
    \"slackId\": \"$FALLBACK_SLACK_ID\",
    \"notificationAllow\": false,
    \"role\": \"SELLER\"
  }")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
print_result "Seller (seller99)" "$HTTP_CODE" "$BODY"

# 3. Manager
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/auth/admin/signup" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"manager1\",
    \"name\": \"테스트 매니저\",
    \"email\": \"manager1@test.com\",
    \"phone\": \"010-1234-0003\",
    \"password\": \"Test1234!\",
    \"slackId\": \"$FALLBACK_SLACK_ID\",
    \"notificationAllow\": false,
    \"role\": \"MANAGER\",
    \"adminKey\": \"$MANAGER_KEY\"
  }")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
print_result "Manager (manager1)" "$HTTP_CODE" "$BODY"

# 4. Master
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/auth/admin/signup" \
  -H "Content-Type: application/json" \
  -d "{
    \"username\": \"master1\",
    \"name\": \"테스트 마스터\",
    \"email\": \"master1@test.com\",
    \"phone\": \"010-1234-0004\",
    \"password\": \"Test1234!\",
    \"slackId\": \"$FALLBACK_SLACK_ID\",
    \"notificationAllow\": false,
    \"role\": \"MASTER\",
    \"adminKey\": \"$MASTER_KEY\"
  }")
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')
print_result "Master (master1)" "$HTTP_CODE" "$BODY"

echo "---"
echo "testseller로 로그인 후 사과 상품을 생성합니다."
echo "---"

# seller99 로그인
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "seller99",
    "password": "Test1234!"
  }')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" != "200" ]; then
  echo "[실패] seller99 로그인 (HTTP $HTTP_CODE)"
  echo "  응답: $BODY"
  exit 1
fi

ACCESS_TOKEN=$(echo "$BODY" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

if [ -z "$ACCESS_TOKEN" ]; then
  echo "[실패] accessToken 추출 실패"
  echo "  응답: $BODY"
  exit 1
fi

echo "[성공] seller99 로그인"

# 사과 상품 생성
RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/v1/products" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -d '{
    "name": "사과",
    "description": "신선한 사과입니다.",
    "quantity": "10개"
  }')
HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | sed '$d')

if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "201" ]; then
  echo "[성공] 사과 상품 생성 (HTTP $HTTP_CODE)"
else
  echo "[실패] 사과 상품 생성 (HTTP $HTTP_CODE)"
  echo "  응답: $BODY"
fi

echo "---"
echo "완료"
