package com.example.characterchat.analysis.image.application;

import com.example.characterchat.analysis.entity.domain.EntityType;

public record VisualEntityDraft(
		String subjectKey,
		Long existingCandidateId,
		EntityType entityType,
		String observedName,
		String description,
		double confidence,
		Long imageId,
		String sourceType
) {}
