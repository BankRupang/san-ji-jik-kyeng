package com.bankrupang.sanjijk.user.presentation.controller;

import com.bankrupang.sanjijk.common.response.ApiResponse;
import com.bankrupang.sanjijk.user.application.service.UserService;
import com.bankrupang.sanjijk.user.presentation.dto.response.UserInfoResponse;
import com.bankrupang.sanjijk.user.presentation.dto.response.UserNotifyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    @GetMapping("/{userId}/notify-allow")
    public ResponseEntity<ApiResponse<UserNotifyResponse>> getNotificationAllow(@PathVariable UUID userId) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok(userService.getNotificationAllow(userId)));
    }

    @GetMapping("/{userId}/userInfo")
    public ResponseEntity<ApiResponse<UserInfoResponse>> getUserInfo(@PathVariable UUID userId) {
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok(userService.getUserInfo(userId)));
    }
}
