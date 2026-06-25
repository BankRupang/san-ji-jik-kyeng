package com.bankrupang.sanjijk.payment.domian.enums;

public enum OutboxStatus {
    PENDING,      // 발행 대기
    IN_PROGRESS,  // 처리 중 (선점 상태 - 중복 발행 방지)
    PUBLISHED,    // 발행 완료
    FAILED        // 발행 실패 (재시도 대상)
}
