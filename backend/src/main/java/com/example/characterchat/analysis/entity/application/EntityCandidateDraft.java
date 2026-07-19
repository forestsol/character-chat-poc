package com.example.characterchat.analysis.entity.application;

import com.example.characterchat.analysis.entity.domain.EntityType;

import java.util.List;

public record EntityCandidateDraft(
		EntityType entityType,
		String canonicalName,
		String description,
		double confidence,
		List<String> aliases,
		List<MentionDraft> mentions
) {}
