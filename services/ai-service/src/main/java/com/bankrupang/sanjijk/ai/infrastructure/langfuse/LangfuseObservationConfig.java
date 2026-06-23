package com.bankrupang.sanjijk.ai.infrastructure.langfuse;

import io.micrometer.common.KeyValues;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Collectors;

@Configuration
public class LangfuseObservationConfig {

    @Bean
    public ChatModelObservationConvention chatModelObservationConvention() {
        return new DefaultChatModelObservationConvention() {
            @Override
            public KeyValues getHighCardinalityKeyValues(ChatModelObservationContext context) {
                KeyValues keyValues = super.getHighCardinalityKeyValues(context);

                if (context.getRequest() != null && !context.getRequest().getInstructions().isEmpty()) {
                    String promptText = context.getRequest().getInstructions().stream()
                            .map(msg -> "[" + msg.getMessageType() + "] " + msg.getText())
                            .collect(Collectors.joining("\n"));
                    keyValues = keyValues.and("gen_ai.prompt", promptText);
                }

                ChatResponse response = context.getResponse();
                if (response != null && response.getResult() != null) {
                    Generation generation = response.getResult();
                    if (generation.getOutput() != null && generation.getOutput().getText() != null) {
                        keyValues = keyValues.and("gen_ai.completion", generation.getOutput().getText());
                    }
                }

                return keyValues;
            }
        };
    }
}
