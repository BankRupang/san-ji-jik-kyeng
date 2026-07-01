#!/bin/bash
set -e

SCRIPT_DIR=$(dirname "$(readlink -f "$0")")
source "${SCRIPT_DIR}/env.sh"

cd "${REPO_DIR}"

# 환경변수 조회
KAFKA_PRIVATE_IP=$(aws ssm get-parameter \
  --name /sanji/prod/kafka/private-ip \
  --query Parameter.Value \
  --output text \
  --region "${REGION}")
export KAFKA_PRIVATE_IP

GRAFANA_ADMIN_PASSWORD=$(aws ssm get-parameter \
  --name /sanji/prod/monitoring/grafana-admin-password \
  --with-decryption \
  --query Parameter.Value \
  --output text \
  --region "${REGION}")

SLACK_WEBHOOK_URL=$(aws ssm get-parameter \
  --name /sanji/prod/monitoring/slack-webhook-url \
  --with-decryption \
  --query Parameter.Value \
  --output text \
  --region "${REGION}")

LANGFUSE_NEXTAUTH_SECRET=$(aws ssm get-parameter \
  --name /sanji/prod/langfuse/nextauth-secret \
  --with-decryption \
  --query Parameter.Value \
  --output text \
  --region "${REGION}")

LANGFUSE_SALT=$(aws ssm get-parameter \
  --name /sanji/prod/langfuse/salt \
  --with-decryption \
  --query Parameter.Value \
  --output text \
  --region "${REGION}")

DB_PASSWORD=$(aws ssm get-parameter \
  --name /sanji/prod/db/password \
  --with-decryption \
  --query Parameter.Value \
  --output text \
  --region "${REGION}")

RDS_HOST=$(aws rds describe-db-instances \
  --query "DBInstances[?DBInstanceIdentifier=='sanji-prod-postgres'].Endpoint.Address" \
  --output text \
  --region "${REGION}")

IMDS_TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
MONITORING_PUBLIC_IP=$(curl -s -H "X-aws-ec2-metadata-token: ${IMDS_TOKEN}" http://169.254.169.254/latest/meta-data/public-ipv4)
MONITORING_PRIVATE_IP=$(curl -s -H "X-aws-ec2-metadata-token: ${IMDS_TOKEN}" http://169.254.169.254/latest/meta-data/local-ipv4)
export MONITORING_PRIVATE_IP

# .env 파일 생성
cat > .env <<EOF
KAFKA_PRIVATE_IP=${KAFKA_PRIVATE_IP}
GRAFANA_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD}
SLACK_WEBHOOK_URL=${SLACK_WEBHOOK_URL}
LANGFUSE_DATABASE_URL=postgresql://sanji:${DB_PASSWORD}@${RDS_HOST}:5432/sanji?schema=langfuse_schema
LANGFUSE_NEXTAUTH_SECRET=${LANGFUSE_NEXTAUTH_SECRET}
LANGFUSE_NEXTAUTH_URL=http://${MONITORING_PUBLIC_IP}:3001
LANGFUSE_SALT=${LANGFUSE_SALT}
EOF
chmod 600 .env

# Prometheus 설정 파일 치환
envsubst '$KAFKA_PRIVATE_IP $MONITORING_PRIVATE_IP' < monitoring/prometheus/prometheus.prod.yml.template > monitoring/prometheus/prometheus.prod.yml

# ecs-targets.json 초기화 (없을 때만)
[ -f monitoring/prometheus/ecs-targets.json ] || echo "[]" > monitoring/prometheus/ecs-targets.json
chown ec2-user:ec2-user monitoring/prometheus/ecs-targets.json

# ECS 디스커버리 크론 등록
chmod +x monitoring/prometheus/ecs-discovery.sh
sudo mkdir -p /etc/cron.d
echo "*/5 * * * * ec2-user ${REPO_DIR}/monitoring/prometheus/ecs-discovery.sh >> /home/ec2-user/ecs-discovery.log 2>&1" \
  | sudo tee /etc/cron.d/ecs-discovery > /dev/null
sudo -u ec2-user "${REPO_DIR}/monitoring/prometheus/ecs-discovery.sh" || true

# 컨테이너 실행
docker compose -f docker-compose.monitoring.yml pull
docker compose -f docker-compose.monitoring.yml up -d
docker compose -f docker-compose.monitoring.yml restart prometheus
