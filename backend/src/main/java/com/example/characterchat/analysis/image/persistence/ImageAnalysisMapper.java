package com.example.characterchat.analysis.image.persistence;

import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityMention;
import com.example.characterchat.analysis.image.domain.ExtractedFact;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ImageAnalysisMapper {
	void deleteImageFactsByBookId(Long bookId);
	void deleteImageMentionsByBookId(Long bookId);
	void deleteImageCandidatesByBookId(Long bookId);
	void insertImageCandidate(EntityCandidate candidate);
	void insertImageMention(EntityMention mention);
	void insertFact(ExtractedFact fact);
	List<ExtractedFact> findFactsByBookId(Long bookId);
	void updateBookStatus(Long bookId);
}
