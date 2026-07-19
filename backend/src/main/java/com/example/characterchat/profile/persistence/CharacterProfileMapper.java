package com.example.characterchat.profile.persistence;

import com.example.characterchat.profile.domain.CharacterProfile;
import com.example.characterchat.profile.domain.ProfileEvidence;
import com.example.characterchat.review.domain.CharacterRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CharacterProfileMapper {
	CharacterRecord findChatEnabledCharacterByBookId(Long bookId);
	List<String> findAliasesByCharacterId(Long characterId);
	void deleteProfileByCharacterId(Long characterId);
	void insertProfile(CharacterProfile profile);
	void insertEvidence(ProfileEvidence evidence);
	CharacterProfile findProfileByBookId(Long bookId);
	List<ProfileEvidence> findEvidenceByProfileId(Long profileId);
	void updateBookStatus(Long bookId);
}
