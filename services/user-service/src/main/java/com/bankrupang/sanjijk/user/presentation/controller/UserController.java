package com.bankrupang.sanjijk.user.presentation.controller;

import com.bankrupang.sanjijk.common.response.ApiResponse;
import com.bankrupang.sanjijk.user.application.service.UserService;
import com.bankrupang.sanjijk.user.presentation.dto.request.UserSignupRequest;
import com.bankrupang.sanjijk.user.presentation.dto.response.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/api/v1/auth/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup (
            @RequestBody @Valid UserSignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.create(userService.signup(request)));
    }
}
