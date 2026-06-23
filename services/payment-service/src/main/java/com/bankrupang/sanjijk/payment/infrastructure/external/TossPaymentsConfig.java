package com.bankrupang.sanjijk.payment.infrastructure.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Base64;

@Configuration
public class TossPaymentsConfig {

    @Value("${toss.payments.base-url}")
    private String baseUrl;

    @Value("${toss.payments.secret-key}")
    private String secretKey;

    /**
     * TossPayments 인증 방식:
     * secretKey + ":" 를 Base64 인코딩 → Basic {encoded} 헤더로 전송
     */
    @Bean
    public RestClient tossPaymentsHttpClient() {
        String encoded = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes());

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
