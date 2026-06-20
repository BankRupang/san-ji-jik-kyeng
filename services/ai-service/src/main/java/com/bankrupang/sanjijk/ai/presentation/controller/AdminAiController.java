package com.bankrupang.sanjijk.ai.presentation.controller;

import com.bankrupang.sanjijk.ai.application.service.DocumentIngestionService;
import com.bankrupang.sanjijk.ai.presentation.dto.response.DocumentInfoResponse;
import com.bankrupang.sanjijk.common.response.ApiResponse;
import com.bankrupang.sanjijk.common.response.PageResponse;
import com.bankrupang.sanjijk.common.util.PageableUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "AI Admin", description = "AI 관리자 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai")
public class AdminAiController {

    private final DocumentIngestionService documentIngestionService;

    @Operation(summary = "문서 등록", description = "RAG 챗봇에 사용할 문서를 VectorStore에 등록합니다. (PDF, Word, Excel, txt 등 지원)")
    @PreAuthorize("hasRole('MASTER')")
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> ingestDocument(
            @RequestPart("file") MultipartFile file,
            @NotBlank @RequestParam("source") String source) {
        documentIngestionService.ingest(file, source);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "문서 목록 조회", description = "등록된 문서 목록을 페이징으로 조회합니다.")
    @PreAuthorize("hasRole('MASTER')")
    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<PageResponse<DocumentInfoResponse>>> getDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageableUtils.ofDefault(page, size);
        Page<DocumentInfoResponse> result = documentIngestionService.getDocuments(pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(result)));
    }

    @Operation(summary = "문서 삭제", description = "source 기준으로 문서 전체를 삭제합니다.")
    @PreAuthorize("hasRole('MASTER')")
    @DeleteMapping("/documents/{source}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable String source) {
        documentIngestionService.deleteBySource(source);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @Operation(summary = "문서 재임베딩", description = "기존 문서를 삭제하고 새 파일로 재등록합니다.")
    @PreAuthorize("hasRole('MASTER')")
    @PutMapping(value = "/documents/{source}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> reingestDocument(
            @NotBlank @PathVariable String source,
            @RequestPart("file") MultipartFile file) {
        documentIngestionService.reingest(file, source);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
