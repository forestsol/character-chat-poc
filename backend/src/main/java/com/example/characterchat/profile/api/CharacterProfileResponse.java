package com.example.characterchat.profile.api;

import com.example.characterchat.profile.domain.CharacterProfile;
import com.example.characterchat.profile.domain.ProfileEvidence;

import java.util.List;

public record CharacterProfileResponse(Long id, Long characterId, String storyPoint, String roleDescription,
		String appearance, String personality, String values, String goals, String speechStyle,
		String majorExperiences, String attitudesTowardOthers, String knownFacts, String systemPrompt,
		List<Evidence> evidence) {
	public static CharacterProfileResponse from(CharacterProfile profile, List<ProfileEvidence> evidence) {
		return new CharacterProfileResponse(profile.getId(), profile.getCharacterId(), profile.getStoryPoint(),
				profile.getRoleDescription(), profile.getAppearance(), profile.getPersonality(), profile.getValues(),
				profile.getGoals(), profile.getSpeechStyle(), profile.getMajorExperiences(), profile.getAttitudesTowardOthers(),
				profile.getKnownFacts(), profile.getSystemPrompt(), evidence.stream().map(Evidence::from).toList());
	}
	public record Evidence(String profileField, Long paragraphId, Long imageId, String sourceType,
			String inferenceType, String description, double confidence) {
		static Evidence from(ProfileEvidence e) { return new Evidence(e.profileField(), e.paragraphId(), e.imageId(),
				e.sourceType(), e.inferenceType(), e.description(), e.confidence()); }
	}
}
