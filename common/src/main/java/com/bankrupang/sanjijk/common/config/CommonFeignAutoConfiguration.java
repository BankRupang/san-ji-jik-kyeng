package com.bankrupang.sanjijk.common.config;

import com.bankrupang.sanjijk.common.feign.CommonFeignErrorDecoder;
import com.bankrupang.sanjijk.common.feign.FeignHeaderPropagationInterceptor;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class CommonFeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FeignHeaderPropagationInterceptor.class)
    public FeignHeaderPropagationInterceptor feignHeaderPropagationInterceptor() {
        return new FeignHeaderPropagationInterceptor();
    }

    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    public ErrorDecoder commonFeignErrorDecoder(ObjectMapper objectMapper) {
        return new CommonFeignErrorDecoder(objectMapper);
    }
}
