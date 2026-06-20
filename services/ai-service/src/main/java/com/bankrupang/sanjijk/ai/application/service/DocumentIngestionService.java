package com.bankrupang.sanjijk.ai.application.service;

import com.bankrupang.sanjijk.ai.exception.AiErrorCode;
import com.bankrupang.sanjijk.ai.presentation.dto.response.DocumentInfoResponse;
import com.bankrupang.sanjijk.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final Tika tika;
    private final TokenTextSplitter tokenTextSplitter;

    public void ingest(MultipartFile file, String source) {
        if (file.isEmpty()) {
            throw new BaseException(AiErrorCode.DOCUMENT_EMPTY);
        }
        try {
            String title = extractTitle(file.getOriginalFilename());
            String text = tika.parseToString(file.getInputStream());

            List<Document> documents = splitBySections(text, title, source);
            List<Document> chunks = tokenTextSplitter.apply(documents);
            vectorStore.add(chunks);
            log.info("문서 적재 완료 - source: {}, title: {}, 청크 수: {}", source, title, chunks.size());
        } catch (Exception e) {
            log.error("문서 적재 실패 - source: {}", source, e);
            throw new BaseException(AiErrorCode.DOCUMENT_INGESTION_FAILED);
        }
    }

    @Transactional
    public void reingest(MultipartFile file, String source) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM ai_schema.vector_store WHERE metadata->>'source' = ?", source);
        log.info("재임베딩 - 기존 청크 삭제: {}개", deleted);
        ingest(file, source);
    }

    public void deleteBySource(String source) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM ai_schema.vector_store WHERE metadata->>'source' = ?", source);
        if (deleted == 0) {
            throw new BaseException(AiErrorCode.DOCUMENT_NOT_FOUND);
        }
        log.info("문서 삭제 완료 - source: {}, 삭제된 청크 수: {}", source, deleted);
    }

    public Page<DocumentInfoResponse> getDocuments(Pageable pageable) {
        Long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM (
                    SELECT 1 FROM ai_schema.vector_store
                    GROUP BY metadata->>'title', metadata->>'source'
                ) t
                """, Long.class);

        List<DocumentInfoResponse> content = jdbcTemplate.query("""
                SELECT metadata->>'title' AS title,
                       metadata->>'source' AS source,
                       COUNT(*) AS chunk_count
                FROM ai_schema.vector_store
                GROUP BY metadata->>'title', metadata->>'source'
                ORDER BY metadata->>'source', metadata->>'title'
                LIMIT ? OFFSET ?
                """,
                (rs, rowNum) -> new DocumentInfoResponse(
                        rs.getString("title"),
                        rs.getString("source"),
                        rs.getInt("chunk_count")),
                pageable.getPageSize(),
                pageable.getOffset());

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    // --- 구분자(---) 기준으로 섹션 분리, 없으면 전체를 하나의 Document로
    private List<Document> splitBySections(String text, String title, String source) {
        String[] sections = text.split("(?m)^---$");
        List<Document> docs = new ArrayList<>();
        for (String section : sections) {
            String trimmed = section.trim();
            if (!trimmed.isEmpty()) {
                docs.add(new Document(
                        "title: " + title + " | text: " + trimmed,
                        Map.of("title", title, "source", source)));
            }
        }
        if (docs.isEmpty()) {
            docs.add(new Document(
                    "title: " + title + " | text: " + text,
                    Map.of("title", title, "source", source)));
        }
        return docs;
    }

    private String extractTitle(String filename) {
        if (filename == null || filename.isBlank()) return "unknown";
        return filename.replaceAll("\\.[^.]+$", "");
    }
}
