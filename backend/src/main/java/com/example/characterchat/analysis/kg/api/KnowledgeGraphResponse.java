package com.example.characterchat.analysis.kg.api;

import com.example.characterchat.analysis.kg.domain.EventParticipant;
import com.example.characterchat.analysis.kg.domain.KnowledgeEntity;
import com.example.characterchat.analysis.kg.domain.KnowledgeRelation;
import com.example.characterchat.analysis.kg.domain.StoryEvent;

import java.util.List;

public record KnowledgeGraphResponse(List<Event> events, List<Entity> entities, List<Relation> relations) {
	public static KnowledgeGraphResponse from(List<StoryEvent> events, List<EventParticipant> participants,
			List<KnowledgeEntity> entities, List<KnowledgeRelation> relations) {
		return new KnowledgeGraphResponse(
				events.stream().map(event -> new Event(event.getId(), event.getName(), event.getDescription(),
						event.getSequenceOrder(), event.getConfidence(), event.getReviewStatus(),
						event.getEvidenceParagraphId(), event.getEvidenceImageId(), participants.stream()
								.filter(p -> p.eventId().equals(event.getId())).map(Participant::from).toList())).toList(),
				entities.stream().map(Entity::from).toList(), relations.stream().map(Relation::from).toList());
	}
	public record Event(Long id, String name, String description, Integer sequenceOrder, double confidence,
			String reviewStatus, Long evidenceParagraphId, Long evidenceImageId, List<Participant> participants) {}
	public record Participant(Long knowledgeEntityId, String role, Long evidenceParagraphId, Long evidenceImageId) {
		static Participant from(EventParticipant p) { return new Participant(p.knowledgeEntityId(), p.participantRole(), p.evidenceParagraphId(), p.evidenceImageId()); }
	}
	public record Entity(Long id, String entityType, String referenceType, Long referenceId, String name,
			String description, String reviewStatus) {
		static Entity from(KnowledgeEntity e) { return new Entity(e.getId(), e.getEntityType(), e.getReferenceType(),
				e.getReferenceId(), e.getName(), e.getDescription(), e.getReviewStatus()); }
	}
	public record Relation(Long id, Long sourceEntityId, String relationType, Long targetEntityId, String description,
			double confidence, String reviewStatus, Long evidenceParagraphId, Long evidenceImageId) {
		static Relation from(KnowledgeRelation r) { return new Relation(r.id(), r.sourceEntityId(), r.relationType(),
				r.targetEntityId(), r.description(), r.confidence(), r.reviewStatus(), r.evidenceParagraphId(), r.evidenceImageId()); }
	}
}
