package com.example.characterchat.review.api;

import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.review.domain.CharacterRecord;

public record CharacterReviewResponse(Long candidateId, String name, String description, double confidence,
		String originSource, String reviewStatus, String recommendedRole, String recommendationReason,
		Long mergedIntoCandidateId, Long characterId, String narrativeRole, boolean chatEnabled) {
	public static CharacterReviewResponse from(EntityCandidate candidate, CharacterRecord character) {
		return new CharacterReviewResponse(candidate.getId(), candidate.getCanonicalName(), candidate.getDescription(),
				candidate.getConfidence(), candidate.getOriginSource(), candidate.getReviewStatus(), candidate.getRecommendedRole(),
				candidate.getRoleRecommendationReason(), candidate.getMergedIntoCandidateId(),
				character == null ? null : character.id(), character == null ? null : character.narrativeRole(),
				character != null && character.chatEnabled());
	}
}
