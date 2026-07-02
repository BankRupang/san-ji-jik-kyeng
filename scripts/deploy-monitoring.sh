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

LANGFUSE_INIT_PROJECT_PUBLIC_KEY=$(aws ssm get-parameter \
  --name /sanji/prod/langfuse/public-key \
  --with-decryption \
  --query Parameter.Value \
  --output text \
  --region "${REGION}")

LANGFUSE_INIT_PROJECT_SECRET_KEY=$(aws ssm get-parameter \
  --name /sanji/prod/langfuse/secret-key \
  --with-decryption \
  --query Parameter.Value \
  --output text \
  --region "${REGION}")

LANGFUSE_INIT_USER_PASSWORD=$(aws ssm get-parameter \
  --name /sanji/prod/langfuse/init-user-password \
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

# KAFKA_PRIVATE_IP는 쉼표로 구분된 여러 IP(포트 포함 여부는 무관)이므로
# 브로커별로 IP를 뽑아 :9092를 붙인 kafka-ui용 bootstrap 문자열을 만듭니다.
# (IP만 있는 값과 IP:PORT 값이 섞여 있어도 %%:*로 포트를 떼고 다시 붙입니다)
IFS=',' read -ra KAFKA_IPS <<< "${KAFKA_PRIVATE_IP}"
KAFKA_BOOTSTRAP_SERVERS=""
for addr in "${KAFKA_IPS[@]}"; do
  ip="${addr%%:*}"
  KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:+${KAFKA_BOOTSTRAP_SERVERS},}${ip}:9092"
done
export KAFKA_BOOTSTRAP_SERVERS

# .env 파일 생성
cat > .env <<EOF
KAFKA_PRIVATE_IP=${KAFKA_PRIVATE_IP}
KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS}
GRAFANA_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD}
SLACK_WEBHOOK_URL=${SLACK_WEBHOOK_URL}
LANGFUSE_DATABASE_URL=postgresql://sanji:${DB_PASSWORD}@${RDS_HOST}:5432/sanji?schema=langfuse_schema
LANGFUSE_NEXTAUTH_SECRET=${LANGFUSE_NEXTAUTH_SECRET}
LANGFUSE_NEXTAUTH_URL=http://${MONITORING_PUBLIC_IP}:3001
LANGFUSE_SALT=${LANGFUSE_SALT}
LANGFUSE_INIT_PROJECT_PUBLIC_KEY=${LANGFUSE_INIT_PROJECT_PUBLIC_KEY}
LANGFUSE_INIT_PROJECT_SECRET_KEY=${LANGFUSE_INIT_PROJECT_SECRET_KEY}
LANGFUSE_INIT_USER_PASSWORD=${LANGFUSE_INIT_USER_PASSWORD}
EOF
chmod 600 .env

# Prometheus 설정 파일 생성
# envsubst 대신 직접 생성: KAFKA_PRIVATE_IP가 쉼표로 구분된 여러 IP이므로
# targets 블록을 루프로 확장해야 합니다.
cat > monitoring/prometheus/prometheus.prod.yml <<'YAML_HEADER'
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  # Kafka EC2: JMX Exporter(7071) + Node Exporter(9100)
  - job_name: "kafka"
    static_configs:
      - targets:
YAML_HEADER

for addr in "${KAFKA_IPS[@]}"; do
  ip="${addr%%:*}"   # 포트가 붙어 있어도 제거 (IP:9092 → IP)
  printf '          - "%s:7071"\n          - "%s:9100"\n' "${ip}" "${ip}" >> monitoring/prometheus/prometheus.prod.yml
done

cat >> monitoring/prometheus/prometheus.prod.yml <<YAML_FOOTER

  # ECS Fargate 앱: ecs-discovery.sh(cron 5분)가 주기적으로 태스크 IP를 JSON 파일에 기록
  - job_name: "ecs-apps"
    metrics_path: "/actuator/prometheus"
    file_sd_configs:
      - files:
          - /etc/prometheus/ecs-targets.json
        refresh_interval: 5m

  # 모니터링 EC2: Node Exporter(9100)
  - job_name: "monitoring"
    static_configs:
      - targets:
          - "${MONITORING_PRIVATE_IP}:9100"
YAML_FOOTER

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
