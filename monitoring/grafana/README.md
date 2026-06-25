# Grafana Alert 규칙

`alert-rules.yml`에 정의된 규칙 목록입니다. `localhost:3000/alerting` 에서 현재 상태를 확인할 수 있습니다.

## 활성 규칙

| 그룹 | Alert | 조건 | 지속 시간 | severity |
| --- | --- | --- | --- | --- |
| availability | ServiceDown | 스크레이프 실패 | 1분 | critical |
| jvm | HighJvmHeapUsage | Heap 사용률 > 80% | 5분 | warn |
| jvm | HighGcOverhead | GC Overhead > 30% | 5분 | warn |
| jvm | HighThreadCount | 라이브 스레드 > 300개 | 5분 | warn |
| http | High5xxErrorRate | 5xx 에러율 > 0.1% | 1분 | critical |
| http | HighResponseTime | 최대 응답시간 > 3초 | 5분 | warn |
| process | HighProcessCpuUsage | JVM 프로세스 CPU > 70% | 5분 | warn |

## 비활성 규칙

커스텀 메트릭 구현 후 `alert-rules.yml`에 추가합니다.

| 그룹 | Alert | 조건 | 활성화 조건 |
| --- | --- | --- | --- |
| bid | HighBidFailureRate | 입찰 실패율 > 1% | `bid_failure_total`, `bid_success_total` 구현 |
| bid | HighLockFailureRate | 락 실패율 > 5% | 동일 |
| bid | WebSocketConnectionsDrop | WS 연결 50% 급감 | `websocket_connections_active` 구현 |
| notification | HighNotificationPending | PENDING > 100건 | `notification_pending_total` 구현 |
| notification | HighNotificationFailed | FAILED > 10건 | `notification_failed_total` 구현 |
| notification | HighSlackFailureRate | Slack 실패율 > 5% | `slack_send_failure_total` 구현 |
| kafka | HighKafkaConsumerLag | Consumer Lag > 1,000건 5분 | JMX Exporter 연동 |
