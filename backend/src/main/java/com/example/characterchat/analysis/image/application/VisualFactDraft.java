package com.example.characterchat.analysis.image.application;

public record VisualFactDraft(
		String subjectKey,
		String factType,
		String value,
		String sourceType,
		String status,
		double confidence,
		Long imageId,
		String description
) {}
