package com.bankrupang.sanjijk.ai.application.service;

import com.bankrupang.sanjijk.ai.exception.AiErrorCode;
import com.bankrupang.sanjijk.ai.infrastructure.persistence.VectorStoreRepository;
import com.bankrupang.sanjijk.ai.presentation.dto.response.DocumentInfoResponse;
import com.bankrupang.sanjijk.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final VectorStoreRepository vectorStoreRepository;
    private final Tika tika;
    private final TokenTextSplitter tokenTextSplitter;

    public void ingest(MultipartFile file, String source) {
        if (file.isEmpty()) {
            throw new BaseException(AiErrorCode.DOCUMENT_EMPTY);
        }
        String title = extractTitle(file.getOriginalFilename());
        String version = UUID.randomUUID().toString();
        List<Document> chunks = parseAndSplit(file, title, source, version);
        try {
            vectorStore.add(chunks);
            log.info("문서 적재 완료 - source: {}, title: {}, 청크 수: {}", source, title, chunks.size());
        } catch (Exception e) {
            log.error("벡터 스토어 저장 실패 - source: {}", source, e);
            throw new BaseException(AiErrorCode.DOCUMENT_INGESTION_FAILED);
        }
    }

    public void reingest(MultipartFile file, String source) {
        if (file.isEmpty()) {
            throw new BaseException(AiErrorCode.DOCUMENT_EMPTY);
        }
        String title = extractTitle(file.getOriginalFilename());
        String newVersion = UUID.randomUUID().toString();
        List<Document> chunks = parseAndSplit(file, title, source, newVersion);
        // 새 버전 먼저 적재: 실패 시 기존 데이터 보존
        try {
            vectorStore.add(chunks);
        } catch (Exception e) {
            log.error("벡터 스토어 저장 실패 - source: {}", source, e);
            throw new BaseException(AiErrorCode.DOCUMENT_INGESTION_FAILED);
        }
        // 새 버전 제외 기존 청크 삭제 (version 없는 구버전 포함)
        int deleted = vectorStoreRepository.deleteBySourceExcludingVersion(source, newVersion);
        log.info("재임베딩 완료 - source: {}, title: {}, 삭제: {}개, 신규: {}개", source, title, deleted, chunks.size());
    }

    public void deleteBySource(String source) {
        int deleted = vectorStoreRepository.deleteBySource(source);
        if (deleted == 0) {
            throw new BaseException(AiErrorCode.DOCUMENT_NOT_FOUND);
        }
        log.info("문서 삭제 완료 - source: {}, 삭제된 청크 수: {}", source, deleted);
    }

    public Page<DocumentInfoResponse> getDocuments(Pageable pageable) {
        long total = vectorStoreRepository.countDistinctDocuments();
        List<DocumentInfoResponse> content = vectorStoreRepository.findDocuments(
                pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(content, pageable, total);
    }

    private List<Document> parseAndSplit(MultipartFile file, String title, String source, String version) {
        try (InputStream inputStream = file.getInputStream()) {
            String text = tika.parseToString(inputStream);
            List<Document> documents = splitBySections(text, title, source, version);
            return tokenTextSplitter.apply(documents);
        } catch (TikaException e) {
            log.error("Tika 파싱 실패 - source: {}", source, e);
            throw new BaseException(AiErrorCode.DOCUMENT_INGESTION_FAILED);
        } catch (IOException e) {
            log.error("파일 읽기 실패 - source: {}", source, e);
            throw new BaseException(AiErrorCode.DOCUMENT_INGESTION_FAILED);
        } catch (Exception e) {
            log.error("문서 파싱 중 예상치 못한 오류 - source: {}", source, e);
            throw new BaseException(AiErrorCode.DOCUMENT_INGESTION_FAILED);
        }
    }

    private List<Document> splitBySections(String text, String title, String source, String version) {
        String[] sections = text.split("(?m)^---$");
        List<Document> docs = new ArrayList<>();
        for (String section : sections) {
            String trimmed = section.trim();
            if (!trimmed.isEmpty()) {
                docs.add(new Document(
                        "title: " + title + " | text: " + trimmed,
                        Map.of("title", title, "source", source, "version", version)));
            }
        }
        if (docs.isEmpty()) {
            docs.add(new Document(
                    "title: " + title + " | text: " + text,
                    Map.of("title", title, "source", source, "version", version)));
        }
        return docs;
    }

    private String extractTitle(String filename) {
        if (filename == null || filename.isBlank()) return "unknown";
        return filename.replaceAll("\\.[^.]+$", "");
    }
}
