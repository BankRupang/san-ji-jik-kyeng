package com.bankrupang.sanjijk.ai.application.service;

import com.bankrupang.sanjijk.ai.infrastructure.config.ChatClientConfig;
import com.bankrupang.sanjijk.ai.infrastructure.langfuse.LangfuseTraceContext;
import com.bankrupang.sanjijk.ai.domain.entity.ChatMessage;
import com.bankrupang.sanjijk.ai.domain.entity.ChatSession;
import com.bankrupang.sanjijk.ai.domain.enums.ChatRole;
import com.bankrupang.sanjijk.ai.domain.repository.ChatMessageRepository;
import com.bankrupang.sanjijk.ai.domain.repository.ChatSessionRepository;
import com.bankrupang.sanjijk.ai.exception.AiErrorCode;
import com.bankrupang.sanjijk.ai.infrastructure.ai.HybridSearchService;
import com.bankrupang.sanjijk.common.exception.BaseException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatService {

    private final Set<UUID> processingSessions = ConcurrentHashMap.newKeySet();

    private final ChatClient chatClient;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final HybridSearchService hybridSearchService;
    private final TransactionTemplate transactionTemplate;
    private final ObservationRegistry observationRegistry;
    private final LangfuseTraceContext traceContext;
    private final Counter reformulationSuccessCounter;
    private final Counter reformulationFailCounter;
    private final Counter aiResponseFailCounter;

    public ChatService(ChatClient chatClient, ChatSessionRepository sessionRepository,
                       ChatMessageRepository messageRepository, HybridSearchService hybridSearchService,
                       TransactionTemplate transactionTemplate,
                       ObservationRegistry observationRegistry, MeterRegistry meterRegistry,
                       LangfuseTraceContext traceContext) {
        this.chatClient = chatClient;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.hybridSearchService = hybridSearchService;
        this.transactionTemplate = transactionTemplate;
        this.observationRegistry = observationRegistry;
        this.traceContext = traceContext;
        this.reformulationSuccessCounter = Counter.builder("query.reformulation.success")
                .description("쿼리 재작성 성공 횟수")
                .register(meterRegistry);
        this.reformulationFailCounter = Counter.builder("query.reformulation.fail")
                .description("쿼리 재작성 실패 횟수")
                .register(meterRegistry);
        this.aiResponseFailCounter = Counter.builder("ai.response.fail")
                .description("AI 응답 생성 실패 횟수")
                .register(meterRegistry);
    }

    @Value("${ai.chat.session-expire-hours}")
    private int sessionExpireHours;

    @Value("${ai.chat.max-history:10}")
    private int maxHistory;

    @Transactional
    public ChatSession createSession(UUID userId) {
        return sessionRepository.save(ChatSession.create(userId, sessionExpireHours));
    }

    public String chat(UUID sessionId, UUID userId, String userMessage) {
        validateSession(sessionId, userId);

        if (!processingSessions.add(sessionId)) {
            throw new BaseException(AiErrorCode.CHAT_SESSION_ALREADY_PROCESSING);
        }

        try {
            return Observation.createNotStarted("chat.pipeline", observationRegistry)
                    .highCardinalityKeyValue("user.id", userId.toString())
                    .observe(() -> {
                        List<ChatMessage> history = messageRepository
                                .findBySessionIdOrderByIdDesc(sessionId, PageRequest.of(0, maxHistory))
                                .reversed();

                        String traceId = Span.current().getSpanContext().getTraceId();
                        traceContext.put(traceId, "session_id", sessionId.toString());
                        traceContext.put(traceId, "user_id", userId.toString());
                        traceContext.put(traceId, "is_multi_turn", !history.isEmpty());

                        // 2단계: Query Reformulation - 대화 기록 기반 쿼리 재작성
                        String searchQuery = reformulateQuery(userMessage, history);

                        // 3단계: Hybrid Search - RRF 융합 검색
                        List<String> documents = hybridSearchService.search(searchQuery);

                        // 4단계: Context Condensing + 응답 생성 (시스템 프롬프트에 가드레일 포함)
                        String response = generateResponse(userMessage, history, documents);

                        transactionTemplate.executeWithoutResult(status -> {
                            messageRepository.save(ChatMessage.of(sessionId, ChatRole.USER, userMessage));
                            messageRepository.save(ChatMessage.of(sessionId, ChatRole.ASSISTANT, response));
                        });

                        return response;
                    });
        } finally {
            processingSessions.remove(sessionId);
        }
    }

    @Transactional(readOnly = true)
    public Page<ChatSession> getSessions(UUID userId, Pageable pageable) {
        return sessionRepository.findByUserIdAndDeletedAtIsNull(userId, pageable);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getMessages(UUID sessionId, UUID userId) {
        ChatSession session = sessionRepository.findByIdAndDeletedAtIsNull(sessionId)
                .orElseThrow(() -> new BaseException(AiErrorCode.CHAT_SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BaseException(AiErrorCode.CHAT_SESSION_ACCESS_DENIED);
        }
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public void deleteSession(UUID sessionId, UUID userId) {
        ChatSession session = sessionRepository.findByIdAndDeletedAtIsNull(sessionId)
                .orElseThrow(() -> new BaseException(AiErrorCode.CHAT_SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BaseException(AiErrorCode.CHAT_SESSION_ACCESS_DENIED);
        }
        session.softDelete(userId);
    }

    private void validateSession(UUID sessionId, UUID userId) {
        boolean expired = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            ChatSession session = sessionRepository.findByIdAndDeletedAtIsNull(sessionId)
                    .orElseThrow(() -> new BaseException(AiErrorCode.CHAT_SESSION_NOT_FOUND));
            if (!session.getUserId().equals(userId)) {
                throw new BaseException(AiErrorCode.CHAT_SESSION_ACCESS_DENIED);
            }
            if (session.isExpired()) {
                session.expire();
                sessionRepository.save(session);
                return true;
            }
            return false;
        }));
        if (expired) {
            throw new BaseException(AiErrorCode.CHAT_SESSION_EXPIRED);
        }
    }

    // 단일 턴이면 원본 쿼리 그대로, 멀티 턴이면 LLM으로 독립적 쿼리 재작성
    private String reformulateQuery(String userMessage, List<ChatMessage> history) {
        if (history.isEmpty()) {
            return userMessage;
        }
        String historyText = history.stream()
                .map(m -> m.getRole().name() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        try {
            String reformulated = chatClient.prompt()
                    .system("""
                            대화 기록을 참고하여 마지막 질문을 독립적인 검색 쿼리로 재작성하세요.
                            대화 맥락이 반영된 단일 문장으로만 응답하고 부연 설명은 하지 마세요.
                            플랫폼과 무관한 질문이면 원본 질문을 그대로 반환하세요.
                            """)
                    .user("대화 기록:\n" + historyText + "\n\n재작성할 질문: " + userMessage)
                    .call()
                    .content();
            if (reformulated != null && !reformulated.isBlank()) {
                reformulationSuccessCounter.increment();
                return reformulated;
            }
            reformulationFailCounter.increment();
            return userMessage;
        } catch (Exception e) {
            reformulationFailCounter.increment();
            log.warn("쿼리 재작성 실패, 원본 쿼리 사용 - error: {}", e.getMessage());
            return userMessage;
        }
    }

    private String generateResponse(String userMessage, List<ChatMessage> history, List<String> documents) {
        String context = documents.isEmpty()
                ? "관련 문서 없음"
                : String.join("\n\n---\n\n", documents);

        try {
            String content = chatClient.prompt()
                    .system(ChatClientConfig.BASE_SYSTEM_PROMPT + "\n\n[참고 문서]\n" + context)
                    .messages(toAiMessages(history))
                    .user(userMessage)
                    .call()
                    .content();
            if (content == null || content.isBlank()) {
                throw new BaseException(AiErrorCode.AI_RESPONSE_UNAVAILABLE);
            }
            return content;
        } catch (BaseException e) {
            aiResponseFailCounter.increment();
            log.error("AI 응답 생성 실패 - error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            aiResponseFailCounter.increment();
            log.error("AI 응답 생성 실패", e);
            throw new BaseException(AiErrorCode.AI_RESPONSE_UNAVAILABLE);
        }
    }

    private List<Message> toAiMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(m -> m.getRole() == ChatRole.USER
                        ? (Message) new UserMessage(m.getContent())
                        : new AssistantMessage(m.getContent()))
                .toList();
    }
}
