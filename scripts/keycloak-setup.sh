#!/bin/bash
# Keycloak realm 설정 스크립트
#
# 사용법:
#   bash scripts/keycloak-setup.sh
#
# 전제 조건:
#   - AWS CLI 설치 및 인증 완료
#   - 모니터링 EC2에 realm-export.json 이 배포된 상태
#   - SSM_PARAM_ADMIN_PASSWORD 가 실제 값으로 채워진 상태
#
# 멱등 처리:
#   Keycloak DB(RDS)에 realm이 이미 존재하면 아무것도 하지 않습니다.
#   realm은 RDS에 저장되므로 Keycloak 컨테이너를 재시작해도 사라지지 않습니다.
#   terraform destroy 후 재배포할 때만 realm이 없는 상태가 됩니다.
#
# 동작 방식:
#   모니터링 EC2에서 SSM Run Command로 Keycloak API를 호출합니다.
#   realm-export.json 위치: /home/ec2-user/deploy/infra/keycloak/realm-export.json
#   client-secret은 모니터링 EC2에서 SSM에 직접 저장합니다.

set -euo pipefail

REGION="${REGION:-ap-northeast-2}"
ECS_CLUSTER="${ECS_CLUSTER:-sanji-prod-cluster}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-sanjijk}"
KEYCLOAK_CLIENT_ID="${KEYCLOAK_CLIENT_ID:-user-service}"
KEYCLOAK_PORT="18080"
SSM_PARAM_ADMIN_PASSWORD="${SSM_PARAM_ADMIN_PASSWORD:-/sanji/prod/keycloak/admin-password}"
SSM_PARAM_CLIENT_SECRET="${SSM_PARAM_CLIENT_SECRET:-/sanji/prod/keycloak/client-secret}"

# ----------------------------------------
# 1. 모니터링 EC2 인스턴스 ID 조회
# ----------------------------------------
echo "[1/4] 모니터링 EC2 인스턴스 ID 조회 중..."
MONITORING_ID="${INSTANCE_ID:-}"
if [ -z "${MONITORING_ID}" ]; then
  MONITORING_ID=$(aws ec2 describe-instances \
    --filters "Name=tag:Role,Values=monitoring" "Name=instance-state-name,Values=running" \
    --query "Reservations[0].Instances[0].InstanceId" \
    --region "${REGION}" \
    --output text)
fi

if [ -z "${MONITORING_ID}" ] || [ "${MONITORING_ID}" = "None" ]; then
  echo "오류: 모니터링 EC2를 찾을 수 없습니다."
  exit 1
fi
echo "  인스턴스 ID: ${MONITORING_ID}"

# ----------------------------------------
# 2. Keycloak 태스크 IP 조회 (RUNNING 상태만)
# ----------------------------------------
echo "[2/4] Keycloak 태스크 IP 조회 중..."
KEYCLOAK_TASK_ARN=$(aws ecs list-tasks \
  --cluster "${ECS_CLUSTER}" \
  --service-name keycloak \
  --desired-status RUNNING \
  --region "${REGION}" \
  --query "taskArns[0]" \
  --output text)

if [ -z "${KEYCLOAK_TASK_ARN}" ] || [ "${KEYCLOAK_TASK_ARN}" = "None" ]; then
  echo "오류: 실행 중인 Keycloak 태스크가 없습니다."
  exit 1
fi

KEYCLOAK_IP=$(aws ecs describe-tasks \
  --cluster "${ECS_CLUSTER}" \
  --tasks "${KEYCLOAK_TASK_ARN}" \
  --region "${REGION}" \
  --query "tasks[0].attachments[0].details[?name=='privateIPv4Address'].value" \
  --output text)

if [ -z "${KEYCLOAK_IP}" ] || [ "${KEYCLOAK_IP}" = "None" ]; then
  echo "오류: Keycloak 태스크 IP를 찾을 수 없습니다."
  exit 1
fi
echo "  Keycloak IP: ${KEYCLOAK_IP}"

# ----------------------------------------
# 3. 모니터링 EC2에서 Keycloak 설정 실행
#    - 스크립트를 base64로 인코딩해 JSON 특수문자 이슈 없이 전달
#    - 외부 쉘 변수(${KEYCLOAK_IP} 등)는 heredoc에서 즉시 확장
#    - 내부 쉘 변수(\${ADMIN_PASS} 등)는 EC2에서 실행 시 확장
# ----------------------------------------
echo "[3/4] 모니터링 EC2에서 Keycloak 설정 실행 중..."

SETUP_SCRIPT=$(cat << HEREDOC
#!/bin/bash
set -euo pipefail

KEYCLOAK_BASE="http://${KEYCLOAK_IP}:${KEYCLOAK_PORT}"
REGION="${REGION}"
REALM_FILE="/home/ec2-user/deploy/infra/keycloak/realm-export.json"

echo "  [1/5] admin 패스워드 조회..."
ADMIN_PASS=\$(aws ssm get-parameter \
  --name "${SSM_PARAM_ADMIN_PASSWORD}" \
  --with-decryption \
  --region "\${REGION}" \
  --query "Parameter.Value" \
  --output text)

echo "  [2/5] Keycloak 기동 대기..."
for i in \$(seq 1 30); do
  if curl -sf "\${KEYCLOAK_BASE}/realms/master" > /dev/null 2>&1; then
    echo "    Keycloak 응답 확인."
    break
  fi
  if [ "\${i}" -eq 30 ]; then
    echo "오류: Keycloak이 응답하지 않습니다."
    exit 1
  fi
  echo "    (\${i}/30)"
  sleep 5
done

echo "  [3/5] admin 토큰 발급..."
TOKEN=\$(curl -s -X POST "\${KEYCLOAK_BASE}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "grant_type=password" \
  --data-urlencode "client_id=admin-cli" \
  --data-urlencode "username=admin" \
  --data-urlencode "password=\${ADMIN_PASS}" \
  | jq -r ".access_token")

if [ "\${TOKEN}" = "null" ] || [ -z "\${TOKEN}" ]; then
  echo "오류: 토큰 발급 실패. admin 패스워드를 확인하세요."
  exit 1
fi
echo "    토큰 발급 성공."

echo "  [4/5] ${KEYCLOAK_REALM} realm 확인..."
REALM=\$(curl -s "\${KEYCLOAK_BASE}/admin/realms/${KEYCLOAK_REALM}" \
  -H "Authorization: Bearer \${TOKEN}" | jq -r ".realm // empty")

if [ -n "\${REALM}" ]; then
  echo "    realm이 이미 존재합니다. 설정을 건너뜁니다."
  echo "SKIP:realm_exists"
  exit 0
fi

echo "    realm 없음 - import 시작..."
HTTP_CODE=\$(curl -s -o /dev/null -w "%{http_code}" -X POST "\${KEYCLOAK_BASE}/admin/realms" \
  -H "Authorization: Bearer \${TOKEN}" \
  -H "Content-Type: application/json" \
  --data-binary "@\${REALM_FILE}")
if [ "\${HTTP_CODE}" -lt 200 ] || [ "\${HTTP_CODE}" -ge 300 ]; then
  echo "오류: realm import 실패 (HTTP \${HTTP_CODE})"
  exit 1
fi
echo "    realm import 완료."

echo "  [5/5] ${KEYCLOAK_CLIENT_ID} client-secret 발급 및 SSM 저장..."
CLIENT_UUID=\$(curl -s "\${KEYCLOAK_BASE}/admin/realms/${KEYCLOAK_REALM}/clients?clientId=${KEYCLOAK_CLIENT_ID}" \
  -H "Authorization: Bearer \${TOKEN}" | jq -r ".[0].id")

if [ -z "\${CLIENT_UUID}" ] || [ "\${CLIENT_UUID}" = "null" ]; then
  echo "오류: ${KEYCLOAK_CLIENT_ID} 클라이언트를 찾을 수 없습니다."
  exit 1
fi

NEW_SECRET=\$(curl -s -X POST "\${KEYCLOAK_BASE}/admin/realms/${KEYCLOAK_REALM}/clients/\${CLIENT_UUID}/client-secret" \
  -H "Authorization: Bearer \${TOKEN}" | jq -r ".value")

if [ -z "\${NEW_SECRET}" ] || [ "\${NEW_SECRET}" = "null" ]; then
  echo "오류: client-secret 발급 실패."
  exit 1
fi

aws ssm put-parameter \
  --name "${SSM_PARAM_CLIENT_SECRET}" \
  --value "\${NEW_SECRET}" \
  --type "SecureString" \
  --overwrite \
  --region "\${REGION}" > /dev/null

echo "SETUP_DONE"
HEREDOC
)

SCRIPT_B64=$(echo "${SETUP_SCRIPT}" | base64 -w 0)
CMD="echo ${SCRIPT_B64} | base64 -d | bash"

CMD_ID=$(aws ssm send-command \
  --document-name "AWS-RunShellScript" \
  --targets "[{\"Key\":\"instanceids\",\"Values\":[\"${MONITORING_ID}\"]}]" \
  --parameters "{\"commands\":[\"${CMD}\"]}" \
  --timeout-seconds 600 \
  --region "${REGION}" \
  --query "Command.CommandId" \
  --output text)

echo "  CommandId: ${CMD_ID}"

# ----------------------------------------
# 4. 결과 대기 및 확인 (최대 10분)
# ----------------------------------------
echo "[4/4] 실행 결과 대기 중..."
for i in $(seq 1 120); do
  sleep 5
  STATUS=$(aws ssm get-command-invocation \
    --command-id "${CMD_ID}" \
    --instance-id "${MONITORING_ID}" \
    --region "${REGION}" \
    --query "Status" \
    --output text 2>/dev/null || echo "Pending")

  echo "  상태: ${STATUS} (${i}/120)"

  if [ "${STATUS}" = "Success" ]; then
    echo ""
    OUTPUT=$(aws ssm get-command-invocation \
      --command-id "${CMD_ID}" \
      --instance-id "${MONITORING_ID}" \
      --region "${REGION}" \
      --query "StandardOutputContent" \
      --output text)
    echo "${OUTPUT}"

    if echo "${OUTPUT}" | grep -q "^SKIP:"; then
      echo "Keycloak이 이미 설정되어 있습니다. 건너뜁니다."
      exit 0
    fi

    if echo "${OUTPUT}" | grep -q "^SETUP_DONE"; then
      echo "  SSM 저장 완료: ${SSM_PARAM_CLIENT_SECRET}"
      echo ""
      echo "완료. Deploy ECS 워크플로우를 실행해 새 client-secret을 반영하세요."
      exit 0
    fi

    echo "오류: 예상치 못한 출력입니다."
    exit 1

  elif [ "${STATUS}" = "Failed" ] || [ "${STATUS}" = "Cancelled" ] || [ "${STATUS}" = "TimedOut" ]; then
    echo ""
    echo "오류: Keycloak 설정 실패 (status=${STATUS})."
    aws ssm get-command-invocation \
      --command-id "${CMD_ID}" \
      --instance-id "${MONITORING_ID}" \
      --region "${REGION}" \
      --query "[StandardOutputContent, StandardErrorContent]" \
      --output text
    exit 1
  fi
done

echo "오류: 시간 초과. AWS 콘솔에서 확인하세요."
exit 1
