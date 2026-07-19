package com.example.characterchat.ai;

public record AiTextRequest(String systemPrompt, String userPrompt) {

	public AiTextRequest {
		if (userPrompt == null || userPrompt.isBlank()) {
			throw new IllegalArgumentException("userPrompt는 필수입니다.");
		}
		systemPrompt = systemPrompt == null ? "" : systemPrompt;
	}
}
