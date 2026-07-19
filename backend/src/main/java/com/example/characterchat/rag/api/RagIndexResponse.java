package com.example.characterchat.rag.api;

public record RagIndexResponse(Long bookId, int documentCount, String embeddingModel, int embeddingDimensions,
		String strategyVersion) {
}

