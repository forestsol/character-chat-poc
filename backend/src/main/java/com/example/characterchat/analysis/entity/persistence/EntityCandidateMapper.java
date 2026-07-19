package com.example.characterchat.analysis.entity.persistence;

import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityMention;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EntityCandidateMapper {
	void insertCandidate(EntityCandidate candidate);
	void insertMention(EntityMention mention);
	List<EntityCandidate> findCandidatesByBookId(Long bookId);
	List<EntityMention> findMentionsByBookId(Long bookId);
	void deleteCandidatesByBookId(Long bookId);
	void updateBookStatus(@Param("bookId") Long bookId, @Param("status") String status);
}
