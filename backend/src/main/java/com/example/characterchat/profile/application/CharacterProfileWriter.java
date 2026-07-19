package com.example.characterchat.profile.application;

import com.example.characterchat.profile.domain.CharacterProfile;
import com.example.characterchat.profile.domain.ProfileEvidence;
import com.example.characterchat.profile.persistence.CharacterProfileMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class CharacterProfileWriter {
	private final CharacterProfileMapper mapper;
	public CharacterProfileWriter(CharacterProfileMapper mapper) { this.mapper = mapper; }

	@Transactional
	public void replace(Long bookId, CharacterProfile profile, List<ProfileEvidenceDraft> evidence) {
		mapper.deleteProfileByCharacterId(profile.getCharacterId());
		mapper.insertProfile(profile);
		for (ProfileEvidenceDraft draft : evidence) {
			mapper.insertEvidence(new ProfileEvidence(null, profile.getId(), draft.profileField(), draft.paragraphId(),
					draft.imageId(), draft.sourceType(), draft.inferenceType(), draft.description(), draft.confidence()));
		}
		mapper.updateBookStatus(bookId);
	}
}
