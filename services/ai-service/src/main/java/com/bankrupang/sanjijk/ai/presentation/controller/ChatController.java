package com.bankrupang.sanjijk.ai.presentation.controller;

import com.bankrupang.sanjijk.ai.application.service.ChatService;
import com.bankrupang.sanjijk.ai.presentation.dto.request.ChatRequest;
import com.bankrupang.sanjijk.ai.presentation.dto.response.ChatMessageResponse;
import com.bankrupang.sanjijk.ai.presentation.dto.response.ChatResponse;
import com.bankrupang.sanjijk.ai.presentation.dto.response.ChatSessionResponse;
import com.bankrupang.sanjijk.common.response.ApiResponse;
import com.bankrupang.sanjijk.common.response.PageResponse;
import com.bankrupang.sanjijk.common.util.PageableUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "AI Chat", description = "AI 챗봇 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai")
public class ChatController {

    private final ChatService chatService;

    @Operation(summary = "채팅 세션 생성", description = "새로운 채팅 세션을 생성합니다.")
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<ChatSessionResponse>> createSession(
            @Parameter(description = "사용자 ID (gateway에서 주입)") @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(ChatSessionResponse.from(chatService.createSession(userId))));
    }

    @Operation(summary = "채팅", description = "AI 챗봇과 대화합니다.")
    @PostMapping("/sessions/{sessionId}/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @Parameter(description = "사용자 ID (gateway에서 주입)") @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID sessionId,
            @Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(ChatResponse.of(chatService.chat(sessionId, userId, request.getMessage()))));
    }

    @Operation(summary = "채팅 세션 목록 조회", description = "내 채팅 세션 목록을 페이지네이션으로 조회합니다.")
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<PageResponse<ChatSessionResponse>>> getSessions(
            @Parameter(description = "사용자 ID (gateway에서 주입)") @RequestHeader("X-User-Id") UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageableUtils.ofDefault(page, size);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(chatService.getSessions(userId, pageable).map(ChatSessionResponse::from))));
    }

    @Operation(summary = "채팅 메시지 조회", description = "세션의 전체 메시지 목록을 조회합니다.")
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getMessages(
            @Parameter(description = "사용자 ID (gateway에서 주입)") @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID sessionId) {
        List<ChatMessageResponse> messages = chatService.getMessages(sessionId, userId).stream()
                .map(ChatMessageResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(messages));
    }

    @Operation(summary = "채팅 세션 삭제", description = "채팅 세션을 삭제합니다.")
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @Parameter(description = "사용자 ID (gateway에서 주입)") @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID sessionId) {
        chatService.deleteSession(sessionId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
