#!/bin/bash
set -e

REGION="ap-northeast-2"
REPO_DIR=/home/ec2-user/sanji-jk

cd "${REPO_DIR}"

# 환경변수 조회
IMDS_TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
KAFKA_PRIVATE_IP=$(curl -s -H "X-aws-ec2-metadata-token: ${IMDS_TOKEN}" http://169.254.169.254/latest/meta-data/local-ipv4)
KAFKA_CLUSTER_ID=$(aws ssm get-parameter \
  --name /sanji/prod/kafka/cluster-id \
  --with-decryption \
  --query Parameter.Value \
  --output text \
  --region "${REGION}")

# .env 파일 생성
cat > .env <<EOF
KAFKA_PRIVATE_IP=${KAFKA_PRIVATE_IP}
KAFKA_CLUSTER_ID=${KAFKA_CLUSTER_ID}
EOF
chmod 600 .env

# 컨테이너 실행
docker compose -f docker-compose.kafka.yml pull
docker compose -f docker-compose.kafka.yml up -d
