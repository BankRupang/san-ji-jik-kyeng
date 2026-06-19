package com.bankrupang.sanjijk.common.feign;

import com.bankrupang.sanjijk.common.exception.RemoteServiceException;
import com.bankrupang.sanjijk.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class CommonFeignErrorDecoder implements ErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(CommonFeignErrorDecoder.class);

    private final ObjectMapper objectMapper;

    public CommonFeignErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        try {
            if (response.body() == null) {
                return new RemoteServiceException(response.status(), "REMOTE-998", "원격 서비스 호출 실패 (응답 본문 없음)");
            }
            String body = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
            ErrorResponse errorResponse = objectMapper.readValue(body, ErrorResponse.class);
            return new RemoteServiceException(response.status(), errorResponse.getCode(), errorResponse.getMessage());
        } catch (Exception e) {
            log.warn("Feign 오류 응답 파싱 실패. methodKey={}, status={}", methodKey, response.status(), e);
            return new RemoteServiceException(response.status(), "REMOTE-999", "원격 서비스 호출에 실패했습니다.");
        }
    }
}
