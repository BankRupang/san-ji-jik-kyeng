package com.bankrupang.sanjijk.payment.domian.enums;

public enum PaymentStatus {
    READY, // 결제 생성, 승인 전
    IN_PROGRESS, // 인증 완료, 승인 대기
    DONE, // 결제 승인 완료
    ABORTED, // 결제 승인 실패
    EXPIRED, // 유효시간 초과
    CANCELED, // 환불 완료
    EXPIRE_FAILED, // 만료 처리 중 오류 발생 - 수동 확인 필요
}
