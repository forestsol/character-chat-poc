package com.example.characterchat.analysis.kg.domain;

public record KnowledgeRelation(Long id, Long bookId, Long sourceEntityId, String relationType, Long targetEntityId,
		String description, double confidence, String reviewStatus, Long evidenceParagraphId, Long evidenceImageId) {}
