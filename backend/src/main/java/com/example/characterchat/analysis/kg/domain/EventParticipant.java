package com.example.characterchat.analysis.kg.domain;

public record EventParticipant(Long id, Long eventId, Long knowledgeEntityId, String participantRole,
		Long evidenceParagraphId, Long evidenceImageId) {}
