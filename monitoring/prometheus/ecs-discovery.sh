#!/bin/bash
# ECS Fargate 태스크의 private IP를 조회해서 Prometheus file_sd_configs 형식 JSON으로 저장
# 포트는 서비스 이름 기준으로 매핑 (portMappings가 신뢰할 수 없는 경우 대비)
# cron: */5 * * * * ec2-user /home/ec2-user/sanji-jk/monitoring/prometheus/ecs-discovery.sh >> /var/log/ecs-discovery.log 2>&1
PATH=/usr/local/bin:/usr/bin:/bin

CLUSTER="sanji-prod-cluster"
REGION="ap-northeast-2"
OUTPUT="/home/ec2-user/sanji-jk/monitoring/prometheus/ecs-targets.json"

TASK_ARNS=$(aws ecs list-tasks \
  --cluster "$CLUSTER" \
  --desired-status RUNNING \
  --query "taskArns[]" \
  --output text \
  --region "$REGION")

if [ -z "$TASK_ARNS" ]; then
  echo "[]" > "$OUTPUT"
  echo "$(date): running tasks not found"
  exit 0
fi

aws ecs describe-tasks \
  --cluster "$CLUSTER" \
  --tasks $TASK_ARNS \
  --region "$REGION" \
  --query "tasks[*].{def: taskDefinitionArn, ip: containers[0].networkInterfaces[0].privateIpv4Address}" \
  --output json \
| python3 -c "
import json, sys

# 서비스별 포트 매핑
PORT_MAP = {
    'config-server':      8888,
    'discovery-server':   8761,
    'gateway-server':     8000,
    'user-service':      19091,
    'auction-service':   19092,
    'order-service':     19094,
    'payment-service':   19095,
    'notification-service': 19096,
    'ai-service':        19097,
    'bid-service':       19091,
}

SKIP = {'keycloak'}

tasks = json.load(sys.stdin)
targets = []
for t in tasks:
    ip = t.get('ip')
    if not ip:
        continue
    # 'arn:.../sanji-prod-user-service:3' -> 'user-service'
    family = t.get('def', '').split('/')[-1].rsplit(':', 1)[0]
    svc = family.replace('sanji-prod-', '')
    if svc in SKIP:
        continue
    port = PORT_MAP.get(svc, 8080)
    targets.append({'targets': [f'{ip}:{port}'], 'labels': {'application': family}})
print(json.dumps(targets, indent=2))
" > "$OUTPUT"

echo "$(date): wrote $(python3 -c "import json; print(len(json.load(open('$OUTPUT'))))" ) targets to $OUTPUT"
