package com.example.characterchat.rag.persistence;

import com.example.characterchat.rag.domain.RagDocument;
import com.example.characterchat.rag.domain.RagParagraph;
import com.example.characterchat.rag.domain.RagSearchHit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RagMapper {
	void deleteByBookId(Long bookId);
	void insert(RagDocument document);
	List<RagSearchHit> search(@Param("bookId") Long bookId, @Param("embedding") String embedding,
	                         @Param("topK") int topK);
	List<RagParagraph> findParagraphsByBookId(Long bookId);
	int countByBookId(Long bookId);
	void updateBookStatus(@Param("bookId") Long bookId, @Param("status") String status);
}

