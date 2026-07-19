package com.example.characterchat.profile.domain;

public record ProfileEvidence(Long id, Long characterProfileId, String profileField, Long paragraphId,
		Long imageId, String sourceType, String inferenceType, String description, double confidence) {}
