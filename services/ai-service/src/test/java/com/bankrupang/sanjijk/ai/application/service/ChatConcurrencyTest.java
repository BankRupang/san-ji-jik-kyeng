package com.bankrupang.sanjijk.ai.application.service;

import com.bankrupang.sanjijk.ai.domain.entity.ChatSession;
import com.bankrupang.sanjijk.ai.domain.repository.ChatMessageRepository;
import com.bankrupang.sanjijk.ai.domain.repository.ChatSessionRepository;
import com.bankrupang.sanjijk.ai.exception.AiErrorCode;
import com.bankrupang.sanjijk.ai.infrastructure.ai.HybridSearchService;
import com.bankrupang.sanjijk.common.exception.BaseException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatConcurrencyTest {

    @Mock private ChatClient chatClient;
    @Mock private ChatSessionRepository sessionRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private HybridSearchService hybridSearchService;
    @Mock private TransactionTemplate transactionTemplate;

    private ChatService chatService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        chatService = new ChatService(chatClient, sessionRepository, messageRepository,
                hybridSearchService, transactionTemplate,
                ObservationRegistry.NOOP, new SimpleMeterRegistry());
        ReflectionTestUtils.setField(chatService, "sessionExpireHours", 24);
        ReflectionTestUtils.setField(chatService, "maxHistory", 10);

        lenient().doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        lenient().doAnswer(invocation -> {
            TransactionCallback<?> action = invocation.getArgument(0);
            return action.doInTransaction(null);
        }).when(transactionTemplate).execute(any());
    }

    @Test
    @DisplayName("동일 세션에 5개 동시 요청 - 선착순 1개만 처리, 나머지 4개는 409 반환")
    void concurrentRequests_onlyOneProcessed() throws InterruptedException {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ChatSession session = ChatSession.create(userId, 24);
        ReflectionTestUtils.setField(session, "id", sessionId);

        given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));
        given(messageRepository.findBySessionIdOrderByIdDesc(eq(sessionId), any()))
                .willReturn(Collections.emptyList());

        CountDownLatch lockHeldLatch = new CountDownLatch(1);
        CountDownLatch releaseProcessingLatch = new CountDownLatch(1);
        given(hybridSearchService.search(anyString())).willAnswer(inv -> {
            lockHeldLatch.countDown();
            releaseProcessingLatch.await(5, TimeUnit.SECONDS);
            return List.of("경매 문서");
        });

        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        given(chatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.system(anyString())).willReturn(requestSpec);
        given(requestSpec.messages(anyList())).willReturn(requestSpec);
        given(requestSpec.user(anyString())).willReturn(requestSpec);
        given(requestSpec.call()).willReturn(callSpec);
        given(callSpec.content()).willReturn("AI 응답");

        List<String> successes = new CopyOnWriteArrayList<>();
        List<BaseException> conflicts = new CopyOnWriteArrayList<>();
        CountDownLatch othersDone = new CountDownLatch(4);

        Thread thread1 = new Thread(() -> {
            try {
                successes.add(chatService.chat(sessionId, userId, "질문"));
            } catch (BaseException e) {
                conflicts.add(e);
            }
        });
        thread1.start();

        lockHeldLatch.await(5, TimeUnit.SECONDS);

        for (int i = 0; i < 4; i++) {
            new Thread(() -> {
                try {
                    chatService.chat(sessionId, userId, "질문");
                    successes.add("success");
                } catch (BaseException e) {
                    conflicts.add(e);
                } finally {
                    othersDone.countDown();
                }
            }).start();
        }

        othersDone.await(5, TimeUnit.SECONDS);
        releaseProcessingLatch.countDown();
        thread1.join(5000);

        // then: ConcurrentHashMap.add() 원자성 보장 → 정확히 1개만 성공
        assertThat(successes).hasSize(1);
        assertThat(conflicts).hasSize(4);
        assertThat(conflicts).allSatisfy(e ->
                assertThat(e.getErrorCode()).isEqualTo(AiErrorCode.CHAT_SESSION_ALREADY_PROCESSING));
    }
}
