package com.bankrupang.sanjijk.payment.infrastructure.external;

import com.bankrupang.sanjijk.payment.domian.exception.TossPaymentException;
import com.bankrupang.sanjijk.payment.infrastructure.external.dto.request.TossCancelRequest;
import com.bankrupang.sanjijk.payment.infrastructure.external.dto.request.TossConfirmRequest;
import com.bankrupang.sanjijk.payment.infrastructure.external.dto.response.TossErrorResponse;
import com.bankrupang.sanjijk.payment.infrastructure.external.dto.response.TossPaymentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
// 내 서버가 TossPayments 서버 호출하는 URL
public class TossPaymentsRestClient implements TossPaymentsClient{

    private final RestClient tossPaymentsRestClient;
    private final ObjectMapper objectMapper;

    @Override
    public TossPaymentResponse confirm(TossConfirmRequest request){
        // 주문명(겸매방 이름)은 TossPayments 요청 스펙에 해당이 되지 않아 추가 불가
        // 프론트에서 처리
        log.info("[TossPayments] 결제 승인 요청 - orderId: {}, amount: {}", request.orderId(), request.amount());
        TossPaymentResponse response = tossPaymentsRestClient.post()
                .uri("/v1/payments/confirm")
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    TossErrorResponse error = readError(res);
                    log.error("[TossPayments] 결제 승인 실패 - code: {}, message: {}", error.code(), error.message());
                    throw new TossPaymentException(error.code(), error.message());
                })
                .body(TossPaymentResponse.class);
        log.info("[TossPayments] 결제 승인 완료 - orderId: {}, amount: {}", response.orderId(), response.totalAmount() );
        return response;
    }

    @Override
    public TossPaymentResponse cancel(String paymentKey, TossCancelRequest request){

        // paymentKey로 환불 API 호출하기에 마스킹 처리 필수
        log.info("[TossPayments] 환불 요청 - paymentKey: {}..., cancelAmount: {}",
                paymentKey.substring(0, 8), request.cancelAmount());
        TossPaymentResponse response = tossPaymentsRestClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .body(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    TossErrorResponse error = readError(res);
                    log.error("[TossPayments] 환불 실패 - code: {}, message: {}", error.code(), error.message());
                    throw new TossPaymentException(error.code(), error.message());
                })
                .body(TossPaymentResponse.class);
        log.info("[TossPayments] 환불 완료 - paymentKey: {}..., cancelAmount: {}",
                paymentKey.substring(0, 8), request.cancelAmount());
        return response;
    }

    private TossErrorResponse readError(ClientHttpResponse res){
        try{
            return objectMapper.readValue(res.getBody(), TossErrorResponse.class);
        } catch (Exception e){
            return new TossErrorResponse("UNKNOWN", "TossPayments 오류 응답 파싱 실패");
        }
    }
}
