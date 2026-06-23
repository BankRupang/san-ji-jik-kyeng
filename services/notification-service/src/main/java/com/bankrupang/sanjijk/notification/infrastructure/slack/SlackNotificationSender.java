package com.bankrupang.sanjijk.notification.infrastructure.slack;

import com.bankrupang.sanjijk.notification.application.port.NotificationSendPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class SlackNotificationSender implements NotificationSendPort {

    private static final String SLACK_API_URL = "https://slack.com/api/chat.postMessage";

    private final RestClient restClient;

    @Value("${slack.bot.token}")
    private String botToken;

    public SlackNotificationSender(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public void send(String slackId, String message) {
        Map<String, Object> response = restClient.post()
                .uri(SLACK_API_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + botToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("channel", slackId, "text", message))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});

        if (response == null || !Boolean.TRUE.equals(response.get("ok"))) {
            String error = response != null ? (String) response.get("error") : "null response";
            throw new RuntimeException("Slack 발송 실패: " + error);
        }
    }
}
