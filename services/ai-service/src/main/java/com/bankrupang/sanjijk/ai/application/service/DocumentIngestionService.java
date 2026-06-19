package com.bankrupang.sanjijk.ai.application.service;

import com.bankrupang.sanjijk.ai.exception.AiErrorCode;
import com.bankrupang.sanjijk.common.exception.BaseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;

    public void ingest(String title, String content, String source) {
        try {
            String documentContent = "title: " + title + " | text: " + content;
            Document doc = new Document(documentContent, Map.of("title", title, "source", source));
            vectorStore.add(List.of(doc));
            log.info("문서 적재 완료 - source: {}, title: {}", source, title);
        } catch (Exception e) {
            log.error("문서 적재 실패 - source: {}, title: {}", source, title, e);
            throw new BaseException(AiErrorCode.DOCUMENT_INGESTION_FAILED);
        }
    }
}
