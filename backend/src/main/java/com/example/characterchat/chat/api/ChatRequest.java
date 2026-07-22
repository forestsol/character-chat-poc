package com.example.characterchat.chat.api;

import java.util.List;

public record ChatRequest(String question, List<HistoryMessage> history) {
	public ChatRequest(String question) {
		this(question, List.of());
	}

	public record HistoryMessage(String role, String content) { }
}
