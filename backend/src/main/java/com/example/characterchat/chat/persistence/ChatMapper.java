package com.example.characterchat.chat.persistence;

import com.example.characterchat.chat.domain.DirectKnowledgeRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMapper {
	List<DirectKnowledgeRelation> findDirectRelations(@Param("bookId") Long bookId,
	                                                 @Param("knowledgeEntityId") Long knowledgeEntityId);
	void updateBookStatus(@Param("bookId") Long bookId, @Param("status") String status);
}

