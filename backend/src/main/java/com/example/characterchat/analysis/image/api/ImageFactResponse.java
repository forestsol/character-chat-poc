package com.example.characterchat.analysis.image.api;

import com.example.characterchat.analysis.image.domain.ExtractedFact;

public record ImageFactResponse(Long id, String factType, Long subjectCandidateId, String value,
		String sourceType, String status, double confidence, Long imageId, String description) {
	public static ImageFactResponse from(ExtractedFact fact) {
		return new ImageFactResponse(fact.getId(), fact.getFactType(), fact.getSubjectCandidateId(), fact.getValue(),
				fact.getSourceType(), fact.getStatus(), fact.getConfidence(), fact.getImageId(), fact.getDescription());
	}
}
