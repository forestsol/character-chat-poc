package com.example.characterchat.analysis.kg.application;

import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.kg.domain.EventParticipant;
import com.example.characterchat.analysis.kg.domain.KnowledgeEntity;
import com.example.characterchat.analysis.kg.domain.KnowledgeRelation;
import com.example.characterchat.analysis.kg.domain.StoryEvent;
import com.example.characterchat.analysis.kg.persistence.KnowledgeGraphMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class KnowledgeGraphWriter {
	private final KnowledgeGraphMapper mapper;
	public KnowledgeGraphWriter(KnowledgeGraphMapper mapper) { this.mapper = mapper; }

	@Transactional
	public void replace(Long bookId, List<EntityCandidate> candidates, List<EventDraft> events, List<RelationDraft> relations) {
		mapper.deleteRelationsByBookId(bookId);
		mapper.deleteKnowledgeEntitiesByBookId(bookId);
		mapper.deleteEventsByBookId(bookId);

		Map<Long, Long> candidateEntityIds = new HashMap<>();
		for (EntityCandidate candidate : candidates) {
			KnowledgeEntity entity = entity(bookId, candidate.getEntityType().name(), "ENTITY_CANDIDATE",
					candidate.getId(), candidate.getCanonicalName(), candidate.getDescription());
			mapper.insertKnowledgeEntity(entity);
			candidateEntityIds.put(candidate.getId(), entity.getId());
		}

		for (EventDraft draft : events) {
			StoryEvent event = new StoryEvent();
			event.setBookId(bookId); event.setName(draft.name()); event.setDescription(draft.description());
			event.setSequenceOrder(draft.sequenceOrder()); event.setConfidence(draft.confidence()); event.setReviewStatus("PENDING");
			event.setEvidenceParagraphId(draft.evidence().paragraphId()); event.setEvidenceImageId(draft.evidence().imageId());
			mapper.insertEvent(event);
			KnowledgeEntity eventEntity = entity(bookId, "EVENT", "STORY_EVENT", event.getId(), draft.name(), draft.description());
			mapper.insertKnowledgeEntity(eventEntity);
			for (ParticipantDraft participant : draft.participants()) {
				mapper.insertParticipant(new EventParticipant(null, event.getId(), candidateEntityIds.get(participant.candidateId()),
						participant.role(), participant.evidence().paragraphId(), participant.evidence().imageId()));
			}
		}

		for (RelationDraft draft : relations) {
			mapper.insertRelation(new KnowledgeRelation(null, bookId, candidateEntityIds.get(draft.sourceCandidateId()),
					draft.relationType(), candidateEntityIds.get(draft.targetCandidateId()), draft.description(),
					draft.confidence(), "PENDING", draft.evidence().paragraphId(), draft.evidence().imageId()));
		}
		mapper.updateBookStatus(bookId);
	}

	private KnowledgeEntity entity(Long bookId, String type, String referenceType, Long referenceId, String name, String description) {
		KnowledgeEntity entity = new KnowledgeEntity();
		entity.setBookId(bookId); entity.setEntityType(type); entity.setReferenceType(referenceType);
		entity.setReferenceId(referenceId); entity.setName(name); entity.setDescription(description); entity.setReviewStatus("PENDING");
		return entity;
	}
}
