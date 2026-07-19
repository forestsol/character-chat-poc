package com.example.characterchat.chat.domain;

public record DirectKnowledgeRelation(Long id, String sourceName, String sourceType, String relationType,
		String targetName, String targetType, String description, double confidence, String reviewStatus) {
}

