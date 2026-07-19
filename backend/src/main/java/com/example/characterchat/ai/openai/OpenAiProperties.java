package com.example.characterchat.ai.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai.openai")
public class OpenAiProperties {

	private String apiKey;
	private String model = "gpt-5.6-luna";
	private Duration timeout = Duration.ofSeconds(60);
	private int maxRetries = 2;

	public String getApiKey() { return apiKey; }
	public void setApiKey(String apiKey) { this.apiKey = apiKey; }
	public String getModel() { return model; }
	public void setModel(String model) { this.model = model; }
	public Duration getTimeout() { return timeout; }
	public void setTimeout(Duration timeout) { this.timeout = timeout; }
	public int getMaxRetries() { return maxRetries; }
	public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }
}
