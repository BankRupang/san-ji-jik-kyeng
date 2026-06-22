package com.bankrupang.sanjijk.payment.infrastructure.external;

import com.bankrupang.sanjijk.payment.infrastructure.external.dto.request.TossCancelRequest;
import com.bankrupang.sanjijk.payment.infrastructure.external.dto.request.TossConfirmRequest;
import com.bankrupang.sanjijk.payment.infrastructure.external.dto.response.TossPaymentResponse;

public interface TossPaymentsClient {

    // 실제 동작은 TossPaymentsRestClient에서

    // 결제 승인
    // 결제창 완료 후 TossPayments에 "이 결제 최종 승인 요청"
    TossPaymentResponse confirm(TossConfirmRequest request);

    // 환불/부분 환불
    TossPaymentResponse cancel(String paymentKey,TossCancelRequest request);

    // 결제 단건 조회
    // 결제 상태가 DB랑 TossPayments 서버랑 불일치할 경우 사용
    // TODO: 도전 기능으로 판단.
    // TossPaymentResponse getPayment(String paymentKey);
}
