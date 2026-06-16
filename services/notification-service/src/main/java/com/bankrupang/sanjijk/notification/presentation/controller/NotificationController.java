package com.bankrupang.sanjijk.notification.presentation.controller;

import com.bankrupang.sanjijk.common.response.ApiResponse;
import com.bankrupang.sanjijk.notification.application.service.NotificationService;
import com.bankrupang.sanjijk.notification.presentation.dto.response.NotificationResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<Page<NotificationResponseDto>>> getMyNotifications(
            @RequestHeader("X-User-Id") UUID userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getMyNotifications(userId, pageable)));
    }

    @GetMapping("/notifications/{notificationId}")
    public ResponseEntity<ApiResponse<NotificationResponseDto>> getNotification(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID notificationId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getNotification(userId, notificationId)));
    }

    @GetMapping("/admin/notifications")
    public ResponseEntity<ApiResponse<Page<NotificationResponseDto>>> getAllNotifications(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getAllNotifications(pageable)));
    }
}
