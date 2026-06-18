package com.bankrupang.sanjijk.notification.presentation.controller;

import com.bankrupang.sanjijk.common.exception.BaseException;
import com.bankrupang.sanjijk.common.response.ApiResponse;
import com.bankrupang.sanjijk.common.response.PageResponse;
import com.bankrupang.sanjijk.common.util.PageableUtils;
import com.bankrupang.sanjijk.notification.application.service.NotificationService;
import com.bankrupang.sanjijk.notification.exception.NotificationErrorCode;
import com.bankrupang.sanjijk.notification.presentation.dto.response.NotificationResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Notification", description = "알림 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "내 알림 목록 조회", description = "로그인한 사용자의 알림 목록을 페이지네이션으로 조회합니다.")
    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponseDto>>> getMyNotifications(
            @Parameter(description = "사용자 ID (gateway에서 주입)") @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageableUtils.ofDefault(page, size);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(notificationService.getMyNotifications(userId, pageable))));
    }

    @Operation(summary = "알림 단건 조회", description = "알림 ID로 단건 조회합니다. 본인 알림만 조회 가능합니다.")
    @GetMapping("/notifications/{notificationId}")
    public ResponseEntity<ApiResponse<NotificationResponseDto>> getNotification(
            @Parameter(description = "사용자 ID (gateway에서 주입)") @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID notificationId) {
        return ResponseEntity.ok(ApiResponse.ok(notificationService.getNotification(userId, notificationId)));
    }

    @Operation(summary = "전체 알림 목록 조회 (어드민)", description = "모든 알림을 조회합니다. ADMIN 또는 MASTER 권한 필요.")
    @GetMapping("/admin/notifications")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponseDto>>> getAllNotifications(
            @Parameter(description = "사용자 역할 (gateway에서 주입) - ADMIN 또는 MASTER") @RequestHeader("X-User-Role") String userRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        validateAdminRole(userRole);
        Pageable pageable = PageableUtils.ofDefault(page, size);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(notificationService.getAllNotifications(pageable))));
    }

    private void validateAdminRole(String role) {
        if (!"ADMIN".equals(role) && !"MASTER".equals(role)) {
            throw new BaseException(NotificationErrorCode.NOTIFICATION_ACCESS_DENIED);
        }
    }
}
