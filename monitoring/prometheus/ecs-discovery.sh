#!/bin/bash
# ECS Fargate 태스크의 private IP를 조회해서 Prometheus file_sd_configs 형식 JSON으로 저장
# cron: */5 * * * * /home/ec2-user/sanji-jk/monitoring/prometheus/ecs-discovery.sh >> /var/log/ecs-discovery.log 2>&1

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
tasks = json.load(sys.stdin)
targets = []
for t in tasks:
    ip = t.get('ip')
    if not ip:
        continue
    # 'arn:.../sanji-prod-user-service:3' -> 'sanji-prod-user-service'
    family = t.get('def', '').split('/')[-1].rsplit(':', 1)[0]
    targets.append({'targets': [ip + ':8080'], 'labels': {'application': family}})
print(json.dumps(targets, indent=2))
" > "$OUTPUT"

echo "$(date): wrote $(python3 -c "import json; print(len(json.load(open('$OUTPUT'))))" ) targets to $OUTPUT"
