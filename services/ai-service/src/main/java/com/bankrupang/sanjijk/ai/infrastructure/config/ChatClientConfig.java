package com.bankrupang.sanjijk.ai.infrastructure.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    public static final String BASE_SYSTEM_PROMPT = """
            당신은 산지직경 경매 플랫폼의 전문 상담 챗봇입니다.

            [역할]
            경매 규정, 입찰 방법, 결제 절차, 배송 정책 등 플랫폼 관련 질문에 답변합니다.

            [답변 규칙]
            1. 아래 제공된 [참고 문서]에 기반하여 정확하게 답변하세요.
            2. [참고 문서]에 없는 내용은 "해당 내용은 현재 제공된 정보에서 찾을 수 없습니다"라고 안내하세요.
            3. 경매 플랫폼과 무관한 질문에는 "저는 산지직경 플랫폼 관련 질문만 답변드릴 수 있습니다"라고 답변하세요.
            4. 욕설, 비속어, 부적절한 요청에는 응답하지 마세요.
            5. 답변은 간결하고 명확하게 한국어로 작성하세요.
            """;

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
