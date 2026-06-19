package com.bankrupang.sanjijk.common.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

public class FeignHeaderPropagationInterceptor implements RequestInterceptor {

    private static final List<String> PROPAGATED_HEADERS = List.of(
            "X-User-Id", "X-User-Role"
    );

    @Override
    public void apply(RequestTemplate template) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return;

        HttpServletRequest request = attrs.getRequest();
        for (String header : PROPAGATED_HEADERS) {
            String value = request.getHeader(header);
            if (StringUtils.hasText(value)) {
                template.header(header, value);
            }
        }
    }
}
