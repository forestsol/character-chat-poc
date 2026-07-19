package com.example.characterchat.analysis.entity.domain;

public record EntityMention(
		Long id,
		Long entityCandidateId,
		Long paragraphId,
		Long imageId,
		String mentionText,
		String sourceType,
		double confidence
) {}
