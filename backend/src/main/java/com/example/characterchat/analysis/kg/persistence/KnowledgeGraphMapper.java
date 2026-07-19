package com.example.characterchat.analysis.kg.persistence;

import com.example.characterchat.analysis.kg.domain.EventParticipant;
import com.example.characterchat.analysis.kg.domain.KnowledgeEntity;
import com.example.characterchat.analysis.kg.domain.KnowledgeRelation;
import com.example.characterchat.analysis.kg.domain.StoryEvent;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface KnowledgeGraphMapper {
	void deleteRelationsByBookId(Long bookId);
	void deleteKnowledgeEntitiesByBookId(Long bookId);
	void deleteEventsByBookId(Long bookId);
	void insertEvent(StoryEvent event);
	void insertKnowledgeEntity(KnowledgeEntity entity);
	void insertParticipant(EventParticipant participant);
	void insertRelation(KnowledgeRelation relation);
	List<StoryEvent> findEventsByBookId(Long bookId);
	List<KnowledgeEntity> findEntitiesByBookId(Long bookId);
	List<EventParticipant> findParticipantsByBookId(Long bookId);
	List<KnowledgeRelation> findRelationsByBookId(Long bookId);
	void updateBookStatus(Long bookId);
}
