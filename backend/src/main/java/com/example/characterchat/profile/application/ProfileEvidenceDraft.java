package com.example.characterchat.profile.application;

record ProfileEvidenceDraft(String profileField, Long paragraphId, Long imageId, String sourceType,
		String inferenceType, String description, double confidence) {}
