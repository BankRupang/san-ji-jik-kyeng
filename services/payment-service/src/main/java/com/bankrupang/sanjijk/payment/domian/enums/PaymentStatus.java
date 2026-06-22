package com.bankrupang.sanjijk.payment.domian.enums;

public enum PaymentStatus {
    READY, // 결제 생성, 승인 전
    IN_PROGRESS, // 인증 완료, 승인 대기
    DONE, // 결제 승인 완료
    ABORTED, // 결제 승인 실패
    EXPIRED, // 유효시간 초과
    CANCELED, // 환불 완료
}
