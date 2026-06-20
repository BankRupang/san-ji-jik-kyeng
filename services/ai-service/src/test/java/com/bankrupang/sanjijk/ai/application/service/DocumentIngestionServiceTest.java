package com.bankrupang.sanjijk.ai.application.service;

import com.bankrupang.sanjijk.ai.exception.AiErrorCode;
import com.bankrupang.sanjijk.ai.presentation.dto.response.DocumentInfoResponse;
import com.bankrupang.sanjijk.common.exception.BaseException;
import org.apache.tika.Tika;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private Tika tika;

    @Mock
    private TokenTextSplitter tokenTextSplitter;

    @InjectMocks
    private DocumentIngestionService documentIngestionService;

    @Nested
    @DisplayName("문서 등록")
    class Ingest {

        @Test
        @DisplayName("성공")
        void success() throws Exception {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "auction-rules.txt", "text/plain",
                    "경매 규정 내용입니다.".getBytes());
            given(tika.parseToString(any(InputStream.class))).willReturn("경매 규정 내용입니다.");
            given(tokenTextSplitter.apply(anyList())).willReturn(List.of(new Document("청크 내용")));

            // when
            documentIngestionService.ingest(file, "auction-rules");

            // then
            verify(vectorStore).add(anyList());
        }

        @Test
        @DisplayName("빈 파일이면 DOCUMENT_EMPTY 예외 발생")
        void emptyFile() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.txt", "text/plain", new byte[0]);

            // when & then
            assertThatThrownBy(() -> documentIngestionService.ingest(file, "auction-rules"))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(AiErrorCode.DOCUMENT_EMPTY.getMessage());
        }

        @Test
        @DisplayName("Tika 파싱 실패 시 DOCUMENT_INGESTION_FAILED 예외 발생")
        void tikaParsingFailed() throws Exception {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.txt", "text/plain", "내용".getBytes());
            given(tika.parseToString(any(InputStream.class))).willThrow(new RuntimeException("파싱 실패"));

            // when & then
            assertThatThrownBy(() -> documentIngestionService.ingest(file, "auction-rules"))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(AiErrorCode.DOCUMENT_INGESTION_FAILED.getMessage());
        }

        @Test
        @DisplayName("VectorStore 저장 실패 시 DOCUMENT_INGESTION_FAILED 예외 발생")
        void vectorStoreFailed() throws Exception {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.txt", "text/plain", "내용".getBytes());
            given(tika.parseToString(any(InputStream.class))).willReturn("내용");
            given(tokenTextSplitter.apply(anyList())).willReturn(List.of(new Document("청크")));
            org.mockito.Mockito.doThrow(new RuntimeException("저장 실패")).when(vectorStore).add(anyList());

            // when & then
            assertThatThrownBy(() -> documentIngestionService.ingest(file, "auction-rules"))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(AiErrorCode.DOCUMENT_INGESTION_FAILED.getMessage());
        }
    }

    @Nested
    @DisplayName("문서 재임베딩")
    class Reingest {

        @Test
        @DisplayName("성공 - 기존 청크 삭제 후 재등록")
        void success() throws Exception {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "auction-rules.txt", "text/plain",
                    "새로운 경매 규정입니다.".getBytes());
            given(jdbcTemplate.update(anyString(), anyString())).willReturn(5);
            given(tika.parseToString(any(InputStream.class))).willReturn("새로운 경매 규정입니다.");
            given(tokenTextSplitter.apply(anyList())).willReturn(List.of(new Document("새 청크")));

            // when
            documentIngestionService.reingest(file, "auction-rules");

            // then
            verify(jdbcTemplate).update(anyString(), eq("auction-rules"));
            verify(vectorStore).add(anyList());
        }

        @Test
        @DisplayName("빈 파일이면 DOCUMENT_EMPTY 예외 발생")
        void emptyFile() {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "empty.txt", "text/plain", new byte[0]);
            given(jdbcTemplate.update(anyString(), anyString())).willReturn(3);

            // when & then
            assertThatThrownBy(() -> documentIngestionService.reingest(file, "auction-rules"))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(AiErrorCode.DOCUMENT_EMPTY.getMessage());
        }

        @Test
        @DisplayName("Tika 파싱 실패 시 DOCUMENT_INGESTION_FAILED 예외 발생")
        void tikaParsingFailed() throws Exception {
            // given
            MockMultipartFile file = new MockMultipartFile(
                    "file", "test.txt", "text/plain", "내용".getBytes());
            given(jdbcTemplate.update(anyString(), anyString())).willReturn(3);
            given(tika.parseToString(any(InputStream.class))).willThrow(new RuntimeException("파싱 실패"));

            // when & then
            assertThatThrownBy(() -> documentIngestionService.reingest(file, "auction-rules"))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(AiErrorCode.DOCUMENT_INGESTION_FAILED.getMessage());
        }
    }

    @Nested
    @DisplayName("문서 삭제")
    class DeleteBySource {

        @Test
        @DisplayName("성공")
        void success() {
            // given
            given(jdbcTemplate.update(anyString(), anyString())).willReturn(3);

            // when
            documentIngestionService.deleteBySource("auction-rules");

            // then
            verify(jdbcTemplate).update(anyString(), eq("auction-rules"));
        }

        @Test
        @DisplayName("존재하지 않는 source면 DOCUMENT_NOT_FOUND 예외 발생")
        void notFound() {
            // given
            given(jdbcTemplate.update(anyString(), anyString())).willReturn(0);

            // when & then
            assertThatThrownBy(() -> documentIngestionService.deleteBySource("not-exists"))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(AiErrorCode.DOCUMENT_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("문서 목록 조회")
    class GetDocuments {

        @Test
        @DisplayName("성공")
        @SuppressWarnings("unchecked")
        void success() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            given(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).willReturn(1L);
            given(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object.class), any(Object.class)))
                    .willReturn(List.of(new DocumentInfoResponse("auction-rules", "auction-rules", 7)));

            // when
            Page<DocumentInfoResponse> result = documentIngestionService.getDocuments(pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getSource()).isEqualTo("auction-rules");
        }

        @Test
        @DisplayName("문서가 없으면 빈 페이지 반환")
        @SuppressWarnings("unchecked")
        void empty() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            given(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).willReturn(0L);
            given(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object.class), any(Object.class)))
                    .willReturn(List.of());

            // when
            Page<DocumentInfoResponse> result = documentIngestionService.getDocuments(pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(0);
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("COUNT 쿼리가 null 반환 시 0으로 처리")
        @SuppressWarnings("unchecked")
        void nullTotal() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            given(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).willReturn(null);
            given(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object.class), any(Object.class)))
                    .willReturn(List.of());

            // when
            Page<DocumentInfoResponse> result = documentIngestionService.getDocuments(pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }
}
