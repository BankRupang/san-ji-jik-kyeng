package com.bankrupang.sanjijk.ai.application.service;

import com.bankrupang.sanjijk.ai.domain.entity.ChatMessage;
import com.bankrupang.sanjijk.ai.domain.entity.ChatSession;
import com.bankrupang.sanjijk.ai.domain.enums.ChatRole;
import com.bankrupang.sanjijk.ai.domain.repository.ChatMessageRepository;
import com.bankrupang.sanjijk.ai.domain.repository.ChatSessionRepository;
import com.bankrupang.sanjijk.ai.exception.AiErrorCode;
import com.bankrupang.sanjijk.ai.infrastructure.ai.HybridSearchService;
import com.bankrupang.sanjijk.common.exception.BaseException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatSessionRepository sessionRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private HybridSearchService hybridSearchService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    private ChatService chatService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        chatService = new ChatService(chatClient, sessionRepository, messageRepository,
                hybridSearchService, transactionTemplate,
                ObservationRegistry.NOOP, new SimpleMeterRegistry(), redissonClient);
        ReflectionTestUtils.setField(chatService, "sessionExpireHours", 24);
        ReflectionTestUtils.setField(chatService, "maxHistory", 10);
        ReflectionTestUtils.setField(chatService, "lockTtlSeconds", 60);

        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        lenient().doAnswer(invocation -> {
            TransactionCallback<?> action = invocation.getArgument(0);
            return action.doInTransaction(null);
        }).when(transactionTemplate).execute(any());

        // 기본 락 동작: 정상 획득 (개별 테스트에서 재정의 가능)
        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
        lenient().doReturn(true).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);
    }

    private void mockChatClientResponse(String response) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        given(chatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.system(anyString())).willReturn(requestSpec);
        given(requestSpec.messages(anyList())).willReturn(requestSpec);
        given(requestSpec.user(anyString())).willReturn(requestSpec);
        given(requestSpec.call()).willReturn(callSpec);
        given(callSpec.content()).willReturn(response);
    }

    private ChatSession createSession(UUID userId) {
        ChatSession session = ChatSession.create(userId, 24);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        return session;
    }

    private ChatSession createExpiredSession(UUID userId) {
        ChatSession session = ChatSession.create(userId, 24);
        ReflectionTestUtils.setField(session, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(session, "expiredAt", LocalDateTime.now().minusHours(1));
        return session;
    }

    private ChatMessage createMessage(UUID sessionId, ChatRole role, String content) {
        ChatMessage message = ChatMessage.of(sessionId, role, content);
        ReflectionTestUtils.setField(message, "id", UUID.randomUUID());
        return message;
    }

    @Nested
    @DisplayName("세션 생성")
    class CreateSession {

        @Test
        @DisplayName("성공")
        void success() {
            // given
            UUID userId = UUID.randomUUID();
            ChatSession session = createSession(userId);
            given(sessionRepository.save(any())).willReturn(session);

            // when
            ChatSession result = chatService.createSession(userId);

            // then
            assertThat(result.getUserId()).isEqualTo(userId);
        }
    }

    @Nested
    @DisplayName("채팅")
    class Chat {

        @Test
        @DisplayName("성공 - 단일 턴 (빈 history)")
        void success_singleTurn() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            ChatSession session = createSession(userId);
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));
            given(messageRepository.findBySessionIdOrderByIdDesc(eq(sessionId), any(Pageable.class)))
                    .willReturn(Collections.emptyList());
            given(hybridSearchService.search(anyString())).willReturn(List.of("경매 규정 내용"));
            mockChatClientResponse("안녕하세요! 무엇을 도와드릴까요?");

            // when
            String response = chatService.chat(sessionId, userId, "안녕하세요");

            // then
            assertThat(response).isEqualTo("안녕하세요! 무엇을 도와드릴까요?");
            verify(messageRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("세션이 존재하지 않으면 예외 발생")
        void sessionNotFound() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> chatService.chat(sessionId, userId, "질문"))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(AiErrorCode.CHAT_SESSION_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("다른 사용자의 세션이면 예외 발생")
        void accessDenied() {
            // given
            UUID ownerId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            ChatSession session = createSession(ownerId);
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));

            // when & then
            assertThatThrownBy(() -> chatService.chat(sessionId, otherId, "질문"))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(AiErrorCode.CHAT_SESSION_ACCESS_DENIED.getMessage());
        }

        @Test
        @DisplayName("만료된 세션이면 예외 발생 및 status EXPIRED로 저장")
        void expiredSession() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            ChatSession session = createExpiredSession(userId);
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));

            // when & then
            assertThatThrownBy(() -> chatService.chat(sessionId, userId, "질문"))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(AiErrorCode.CHAT_SESSION_EXPIRED.getMessage());
            verify(sessionRepository, times(1)).save(session);
        }

        @Test
        @DisplayName("성공 - 멀티 턴 (history 있을 때 쿼리 재작성 수행)")
        void success_multiTurn() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            ChatSession session = createSession(userId);
            List<ChatMessage> history = List.of(
                    createMessage(sessionId, ChatRole.USER, "경매가 뭐야?"),
                    createMessage(sessionId, ChatRole.ASSISTANT, "경매는 입찰 방식입니다.")
            );
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));
            given(messageRepository.findBySessionIdOrderByIdDesc(eq(sessionId), any(Pageable.class)))
                    .willReturn(history);
            given(hybridSearchService.search(anyString())).willReturn(List.of("입찰 규정 내용"));
            mockChatClientResponse("재작성된 쿼리 또는 답변");

            // when
            String response = chatService.chat(sessionId, userId, "그러면 입찰은?");

            // then
            assertThat(response).isEqualTo("재작성된 쿼리 또는 답변");
            verify(messageRepository, times(2)).save(any());
        }

        @Test
        @DisplayName("AI 응답이 없으면 예외 발생")
        void aiResponseUnavailable() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            ChatSession session = createSession(userId);
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));
            given(messageRepository.findBySessionIdOrderByIdDesc(eq(sessionId), any(Pageable.class)))
                    .willReturn(Collections.emptyList());
            given(hybridSearchService.search(anyString())).willReturn(Collections.emptyList());
            mockChatClientResponse(null);

            // when & then
            assertThatThrownBy(() -> chatService.chat(sessionId, userId, "질문"))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(AiErrorCode.AI_RESPONSE_UNAVAILABLE.getMessage());
        }

        @Test
        @DisplayName("검색 결과 없어도 응답 생성 성공")
        void success_noSearchResults() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            ChatSession session = createSession(userId);
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));
            given(messageRepository.findBySessionIdOrderByIdDesc(eq(sessionId), any(Pageable.class)))
                    .willReturn(Collections.emptyList());
            given(hybridSearchService.search(anyString())).willReturn(Collections.emptyList());
            mockChatClientResponse("해당 내용은 현재 제공된 정보에서 찾을 수 없습니다.");

            // when
            String response = chatService.chat(sessionId, userId, "관련 없는 질문");

            // then
            assertThat(response).isEqualTo("해당 내용은 현재 제공된 정보에서 찾을 수 없습니다.");
        }

        @Test
        @DisplayName("멀티 턴에서 쿼리 재작성 실패 시 원본 쿼리로 fallback")
        void success_reformulationFallback() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            ChatSession session = createSession(userId);
            List<ChatMessage> history = List.of(
                    createMessage(sessionId, ChatRole.USER, "경매가 뭐야?"),
                    createMessage(sessionId, ChatRole.ASSISTANT, "경매는 입찰 방식입니다.")
            );
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));
            given(messageRepository.findBySessionIdOrderByIdDesc(eq(sessionId), any(Pageable.class)))
                    .willReturn(history);
            given(hybridSearchService.search(anyString())).willReturn(List.of("입찰 규정"));

            ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
            ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
            given(chatClient.prompt()).willReturn(requestSpec);
            given(requestSpec.system(anyString())).willReturn(requestSpec);
            given(requestSpec.messages(anyList())).willReturn(requestSpec);
            given(requestSpec.user(anyString())).willReturn(requestSpec);
            given(requestSpec.call()).willReturn(callSpec);
            given(callSpec.content())
                    .willThrow(new RuntimeException("재작성 API 오류"))
                    .willReturn("fallback 기반 AI 답변");

            // when
            String response = chatService.chat(sessionId, userId, "그러면 입찰은?");

            // then
            assertThat(response).isEqualTo("fallback 기반 AI 답변");
        }
    }

    @Nested
    @DisplayName("분산 락")
    class DistributedLock {

        @Test
        @DisplayName("락 획득 실패 시 409 CONFLICT 반환")
        void lockAcquireFailed_throwsConflict() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            ChatSession session = createSession(userId);
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));
            doReturn(false).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));

            // when & then
            assertThatThrownBy(() -> chatService.chat(sessionId, userId, "질문"))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(AiErrorCode.CHAT_SESSION_ALREADY_PROCESSING.getMessage());
        }

        @Test
        @DisplayName("InterruptedException 발생 시 409 반환 및 스레드 인터럽트 플래그 복원")
        void interrupted_throwsConflictAndRestoresInterruptFlag() throws Exception {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            ChatSession session = createSession(userId);
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));
            doThrow(InterruptedException.class).when(lock).tryLock(anyLong(), anyLong(), any(TimeUnit.class));

            // when & then
            assertThatThrownBy(() -> chatService.chat(sessionId, userId, "질문"))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(AiErrorCode.CHAT_SESSION_ALREADY_PROCESSING.getMessage());

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            Thread.interrupted(); // 다른 테스트에 영향 없도록 플래그 초기화
        }

        @Test
        @DisplayName("처리 중 예외 발생해도 락 반드시 해제")
        void exceptionDuringProcessing_lockAlwaysReleased() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            ChatSession session = createSession(userId);
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));
            given(messageRepository.findBySessionIdOrderByIdDesc(eq(sessionId), any(Pageable.class)))
                    .willReturn(Collections.emptyList());
            given(hybridSearchService.search(anyString())).willThrow(new RuntimeException("검색 오류"));

            // when & then
            assertThatThrownBy(() -> chatService.chat(sessionId, userId, "질문"))
                    .isInstanceOf(RuntimeException.class);

            verify(lock).unlock(); // 예외가 나도 finally에서 해제
        }

        @Test
        @DisplayName("정상 처리 완료 후 락 해제")
        void success_lockReleasedAfterProcessing() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            ChatSession session = createSession(userId);
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));
            given(messageRepository.findBySessionIdOrderByIdDesc(eq(sessionId), any(Pageable.class)))
                    .willReturn(Collections.emptyList());
            given(hybridSearchService.search(anyString())).willReturn(List.of("문서"));
            mockChatClientResponse("AI 응답");

            // when
            chatService.chat(sessionId, userId, "질문");

            // then
            verify(lock).unlock();
        }
    }

    @Nested
    @DisplayName("세션 목록 조회")
    class GetSessions {

        @Test
        @DisplayName("성공")
        void success() {
            // given
            UUID userId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            ChatSession session = createSession(userId);
            given(sessionRepository.findByUserIdAndDeletedAtIsNull(userId, pageable))
                    .willReturn(new PageImpl<>(List.of(session)));

            // when
            Page<ChatSession> result = chatService.getSessions(userId, pageable);

            // then
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("세션이 없으면 빈 페이지 반환")
        void emptyResult() {
            // given
            UUID userId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            given(sessionRepository.findByUserIdAndDeletedAtIsNull(userId, pageable))
                    .willReturn(new PageImpl<>(Collections.emptyList()));

            // when
            Page<ChatSession> result = chatService.getSessions(userId, pageable);

            // then
            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("메시지 조회")
    class GetMessages {

        @Test
        @DisplayName("성공")
        void success() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            ChatSession session = createSession(userId);
            List<ChatMessage> messages = List.of(
                    createMessage(sessionId, ChatRole.USER, "질문"),
                    createMessage(sessionId, ChatRole.ASSISTANT, "답변")
            );
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));
            given(messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)).willReturn(messages);

            // when
            List<ChatMessage> result = chatService.getMessages(sessionId, userId);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getRole()).isEqualTo(ChatRole.USER);
            assertThat(result.get(1).getRole()).isEqualTo(ChatRole.ASSISTANT);
        }

        @Test
        @DisplayName("세션이 존재하지 않으면 예외 발생")
        void sessionNotFound() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> chatService.getMessages(sessionId, userId))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(AiErrorCode.CHAT_SESSION_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("다른 사용자의 세션이면 예외 발생")
        void accessDenied() {
            // given
            UUID ownerId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            ChatSession session = createSession(ownerId);
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));

            // when & then
            assertThatThrownBy(() -> chatService.getMessages(sessionId, otherId))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(AiErrorCode.CHAT_SESSION_ACCESS_DENIED.getMessage());
        }
    }

    @Nested
    @DisplayName("세션 삭제")
    class DeleteSession {

        @Test
        @DisplayName("성공")
        void success() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            ChatSession session = createSession(userId);
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));

            // when
            chatService.deleteSession(sessionId, userId);

            // then
            assertThat(session.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("세션이 존재하지 않으면 예외 발생")
        void sessionNotFound() {
            // given
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> chatService.deleteSession(sessionId, userId))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(AiErrorCode.CHAT_SESSION_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("다른 사용자의 세션이면 예외 발생")
        void accessDenied() {
            // given
            UUID ownerId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            ChatSession session = createSession(ownerId);
            given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));

            // when & then
            assertThatThrownBy(() -> chatService.deleteSession(sessionId, otherId))
                    .isInstanceOf(BaseException.class)
                    .hasMessage(AiErrorCode.CHAT_SESSION_ACCESS_DENIED.getMessage());
        }
    }
}
