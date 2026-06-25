package com.bankrupang.sanjijk.payment.domian.enums;

public enum PaymentType {
    NORMAL,       // 낙찰 잔금 결제
    REPAY,        // 보증금 결제 (경매 입장 시)
    WINNING_REPAY // 낙찰 잔금 재결제 (실패 후 재시도)
}
