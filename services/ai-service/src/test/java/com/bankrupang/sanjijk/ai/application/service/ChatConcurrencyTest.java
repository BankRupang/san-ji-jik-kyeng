package com.bankrupang.sanjijk.ai.application.service;

import com.bankrupang.sanjijk.ai.domain.entity.ChatSession;
import com.bankrupang.sanjijk.ai.domain.repository.ChatMessageRepository;
import com.bankrupang.sanjijk.ai.domain.repository.ChatSessionRepository;
import com.bankrupang.sanjijk.ai.exception.AiErrorCode;
import com.bankrupang.sanjijk.ai.infrastructure.ai.HybridSearchService;
import com.bankrupang.sanjijk.common.exception.BaseException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@Testcontainers
@ExtendWith(MockitoExtension.class)
class ChatConcurrencyTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Mock private ChatClient chatClient;
    @Mock private ChatSessionRepository sessionRepository;
    @Mock private ChatMessageRepository messageRepository;
    @Mock private HybridSearchService hybridSearchService;
    @Mock private TransactionTemplate transactionTemplate;

    private RedissonClient redissonClient;
    private ChatService chatService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        redissonClient = Redisson.create(config);

        chatService = new ChatService(chatClient, sessionRepository, messageRepository,
                hybridSearchService, transactionTemplate,
                ObservationRegistry.NOOP, new SimpleMeterRegistry(), redissonClient);
        ReflectionTestUtils.setField(chatService, "sessionExpireHours", 24);
        ReflectionTestUtils.setField(chatService, "maxHistory", 10);
        ReflectionTestUtils.setField(chatService, "lockTtlSeconds", 60);

        // void 메서드는 doAnswer().when() 사용
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

    @AfterEach
    void tearDown() {
        if (redissonClient != null && !redissonClient.isShutdown()) {
            redissonClient.shutdown();
        }
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

        // 500ms 대기 → 나머지 스레드가 tryLock(waitTime=0) 시도할 충분한 시간 확보
        given(hybridSearchService.search(anyString())).willAnswer(inv -> {
            Thread.sleep(500);
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

        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<String> successes = new CopyOnWriteArrayList<>();
        List<BaseException> conflicts = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    String response = chatService.chat(sessionId, userId, "질문");
                    successes.add(response);
                } catch (BaseException e) {
                    conflicts.add(e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown(); // 5개 스레드 동시 출발
        doneLatch.await(10, TimeUnit.SECONDS);

        // then: Redis SETNX 원자성 보장 → 정확히 1개만 성공
        assertThat(successes).hasSize(1);
        assertThat(conflicts).hasSize(threadCount - 1);
        assertThat(conflicts).allSatisfy(e ->
                assertThat(e.getErrorCode()).isEqualTo(AiErrorCode.CHAT_SESSION_ALREADY_PROCESSING));
    }

    @Test
    @DisplayName("서버 크래시 시뮬레이션 - TTL 만료 후 다음 요청 정상 처리")
    void afterTtlExpiry_nextRequestSucceeds() throws InterruptedException {
        // given
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ChatSession session = ChatSession.create(userId, 24);
        ReflectionTestUtils.setField(session, "id", sessionId);
        ReflectionTestUtils.setField(chatService, "lockTtlSeconds", 1); // TTL 1초로 단축

        given(sessionRepository.findByIdAndDeletedAtIsNull(sessionId)).willReturn(Optional.of(session));
        given(messageRepository.findBySessionIdOrderByIdDesc(eq(sessionId), any()))
                .willReturn(Collections.emptyList());
        given(hybridSearchService.search(anyString())).willReturn(List.of("문서"));

        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        given(chatClient.prompt()).willReturn(requestSpec);
        given(requestSpec.system(anyString())).willReturn(requestSpec);
        given(requestSpec.messages(anyList())).willReturn(requestSpec);
        given(requestSpec.user(anyString())).willReturn(requestSpec);
        given(requestSpec.call()).willReturn(callSpec);
        given(callSpec.content()).willReturn("AI 응답");

        // 다른 스레드(다른 서버 인스턴스)에서 락을 점유한 채 크래시한 상황 시뮬레이션
        // 같은 스레드가 잡으면 Redisson 재진입 락으로 성공해버리므로 반드시 별도 스레드에서 획득
        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        new Thread(() -> {
            redissonClient.getLock("chat:processing:" + sessionId).lock(1, TimeUnit.SECONDS);
            lockAcquiredLatch.countDown();
        }).start();
        lockAcquiredLatch.await();

        // when: 첫 번째 요청 → 락이 잡혀있어 409
        AiErrorCode firstError = catchConflict(() -> chatService.chat(sessionId, userId, "질문"));
        assertThat(firstError).isEqualTo(AiErrorCode.CHAT_SESSION_ALREADY_PROCESSING);

        // TTL 만료 대기 (1초 TTL + 500ms 여유)
        Thread.sleep(1500);

        // when: TTL 만료 후 재요청 → 락 자동 해제로 정상 처리
        String response = chatService.chat(sessionId, userId, "질문");
        assertThat(response).isEqualTo("AI 응답");
    }

    private AiErrorCode catchConflict(Runnable action) {
        try {
            action.run();
            return null;
        } catch (BaseException e) {
            return (AiErrorCode) e.getErrorCode();
        }
    }
}
