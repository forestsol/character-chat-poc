package com.example.characterchat.analysis.kg.application;

import java.util.List;

record EvidenceRef(Long paragraphId, Long imageId) {}
record EventDraft(String name, String description, int sequenceOrder, double confidence, EvidenceRef evidence,
		List<ParticipantDraft> participants) {}
record ParticipantDraft(Long candidateId, String role, EvidenceRef evidence) {}
record RelationDraft(Long sourceCandidateId, String relationType, Long targetCandidateId, String description,
		double confidence, EvidenceRef evidence) {}
