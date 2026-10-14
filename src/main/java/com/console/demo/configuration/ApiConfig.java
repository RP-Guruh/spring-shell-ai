package com.console.demo.configuration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

@Configuration
public class ApiConfig {

    @Bean
    ChatClient.Builder chatClientBuilder(@Qualifier("openAiChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    @Bean
    RestClient restClient(@Value("${spring.ai.openai.base-url}") String baseUrl,
                          @Value("${spring.ai.openai.api-key}") String apiKey) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }
}
