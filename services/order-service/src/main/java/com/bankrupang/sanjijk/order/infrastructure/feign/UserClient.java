package com.bankrupang.sanjijk.order.infrastructure.feign;

import com.bankrupang.sanjijk.common.response.ApiResponse;
import com.bankrupang.sanjijk.order.infrastructure.feign.dto.UserInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserClient {
    @GetMapping("/internal/v1/users/{userId}/user-info")
    ApiResponse<UserInfoResponse> getUserInfo(@PathVariable UUID userId);
}
