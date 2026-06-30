#!/bin/bash
set -e

SCRIPT_DIR=$(dirname "$(readlink -f "$0")")
source "${SCRIPT_DIR}/env.sh"

cd "${REPO_DIR}"

# 환경변수 조회
IMDS_TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
INSTANCE_ID=$(curl -s -H "X-aws-ec2-metadata-token: ${IMDS_TOKEN}" http://169.254.169.254/latest/meta-data/instance-id)
KAFKA_PRIVATE_IP=$(curl -s -H "X-aws-ec2-metadata-token: ${IMDS_TOKEN}" http://169.254.169.254/latest/meta-data/local-ipv4)

# 태그에서 KafkaNodeId 조회
KAFKA_NODE_ID=$(aws ec2 describe-tags \
  --filters "Name=resource-id,Values=${INSTANCE_ID}" "Name=key,Values=KafkaNodeId" \
  --query "Tags[0].Value" \
  --output text \
  --region "${REGION}")

# 클러스터 ID 및 Quorum Voters 조회
KAFKA_CLUSTER_ID=$(aws ssm get-parameter \
  --name /sanji/prod/kafka/cluster-id \
  --with-decryption \
  --query Parameter.Value \
  --output text \
  --region "${REGION}")

KAFKA_CONTROLLER_QUORUM_VOTERS=$(aws ssm get-parameter \
  --name /sanji/prod/kafka/quorum-voters \
  --query Parameter.Value \
  --output text \
  --region "${REGION}")

# 필수 변수 검증 (하나라도 비어 있으면 배포 중단)
for VAR_NAME in KAFKA_PRIVATE_IP KAFKA_NODE_ID KAFKA_CLUSTER_ID KAFKA_CONTROLLER_QUORUM_VOTERS; do
  VAR_VALUE="${!VAR_NAME}"
  if [[ -z "${VAR_VALUE}" || "${VAR_VALUE}" == "None" ]]; then
    echo "[ERROR] 필수 환경 변수 ${VAR_NAME} 를 가져오지 못했습니다. 배포를 중단합니다." >&2
    exit 1
  fi
done

# .env 파일 생성
cat > .env <<EOF
KAFKA_PRIVATE_IP=${KAFKA_PRIVATE_IP}
KAFKA_CLUSTER_ID=${KAFKA_CLUSTER_ID}
KAFKA_NODE_ID=${KAFKA_NODE_ID}
KAFKA_CONTROLLER_QUORUM_VOTERS=${KAFKA_CONTROLLER_QUORUM_VOTERS}
EOF
chmod 600 .env

# 컨테이너 실행
docker compose -f docker-compose.kafka.yml pull
docker compose -f docker-compose.kafka.yml up -d
