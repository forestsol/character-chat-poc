package com.example.characterchat.review.domain;

public record CharacterRecord(Long id, Long bookId, Long candidateId, Long knowledgeEntityId, String name,
		String narrativeRole, boolean chatEnabled, boolean reviewed) {}
