package com.example.characterchat.ai.openai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAiConfiguration {

	@Bean
	OpenAIClient openAIClient(OpenAiProperties properties) {
		if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
			throw new IllegalStateException("AI_PROVIDER=openai일 때 OPENAI_API_KEY는 필수입니다.");
		}
		return OpenAIOkHttpClient.builder()
				.apiKey(properties.getApiKey())
				.timeout(properties.getTimeout())
				.maxRetries(properties.getMaxRetries())
				.build();
	}
}
