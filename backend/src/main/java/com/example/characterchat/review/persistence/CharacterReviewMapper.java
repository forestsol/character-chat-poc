package com.example.characterchat.review.persistence;

import com.example.characterchat.review.domain.CharacterRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CharacterReviewMapper {
	List<CharacterRecord> findCharactersByBookId(Long bookId);
	Long findKnowledgeEntityIdByCandidateId(Long candidateId);
	void updateRecommendation(@Param("candidateId") Long candidateId, @Param("role") String role, @Param("reason") String reason);
	void updateCandidateDecision(@Param("candidateId") Long candidateId, @Param("status") String status, @Param("targetId") Long targetId);
	void updateKnowledgeEntityStatus(@Param("candidateId") Long candidateId, @Param("status") String status);
	void upsertCharacter(@Param("bookId") Long bookId, @Param("candidateId") Long candidateId,
			@Param("knowledgeEntityId") Long knowledgeEntityId, @Param("name") String name,
			@Param("role") String role, @Param("chatEnabled") boolean chatEnabled);
	void deleteAliasesByCandidateId(Long candidateId);
	void insertAliasesFromMentions(Long candidateId);
	void deleteCharacterByCandidateId(Long candidateId);
	void deleteKnowledgeEntityByCandidateId(Long candidateId);
	void moveMentions(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);
	void deleteSourceMentions(Long sourceId);
	void moveFacts(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);
	void mergeKnowledgeGraph(@Param("sourceId") Long sourceId, @Param("targetId") Long targetId);
	void refreshBookStatus(Long bookId);
}
