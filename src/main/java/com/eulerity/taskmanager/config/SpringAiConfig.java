package com.eulerity.taskmanager.config;

import com.eulerity.taskmanager.ai.OpenAiClient;
import com.eulerity.taskmanager.ai.SpringAiOpenAiClient;
import com.eulerity.taskmanager.ai.StubOpenAiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiConfig {

    private static final Logger log = LoggerFactory.getLogger(SpringAiConfig.class);

    @Bean
    public OpenAiClient openAiClient(
            // IMPORTANT: read OPENAI_API_KEY directly, NOT spring.ai.openai.api-key.
            // The yml property has a placeholder default ("stub-mode-no-real-calls")
            // to satisfy Spring AI 1.0.7's Assert.hasText() at boot; reading the
            // property here would defeat stub-mode detection.
            @Value("${OPENAI_API_KEY:}") String apiKey,
            ObjectProvider<ChatClient.Builder> chatClientBuilder) {

        boolean stub = apiKey == null || apiKey.isBlank();
        if (stub) {
            log.warn("============================================================");
            log.warn("OPENAI_API_KEY not set — running with STUB AI client.");
            log.warn("AI endpoints (/tasks/suggest, /tasks/{{id}}/breakdown) will");
            log.warn("return deterministic canned data, not real model output.");
            log.warn("Set OPENAI_API_KEY in your environment to enable real calls.");
            log.warn("============================================================");
            return new StubOpenAiClient();
        }
        ChatClient.Builder builder = chatClientBuilder.getObject();
        return new SpringAiOpenAiClient(builder.build());
    }

    /** Marker bean exposed so `/meta` can answer whether we're in stub mode. */
    @Bean
    public StubModeIndicator stubModeIndicator(OpenAiClient client) {
        return new StubModeIndicator(client instanceof StubOpenAiClient);
    }

    public record StubModeIndicator(boolean stubMode) {}
}
