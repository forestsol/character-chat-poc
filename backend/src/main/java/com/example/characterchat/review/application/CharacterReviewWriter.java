package com.example.characterchat.review.application;

import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.review.persistence.CharacterReviewMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class CharacterReviewWriter {
	private final CharacterReviewMapper mapper;
	public CharacterReviewWriter(CharacterReviewMapper mapper) { this.mapper = mapper; }

	@Transactional
	public void saveRecommendations(Map<Long, RecommendationDraft> recommendations) {
		recommendations.forEach((id, draft) -> mapper.updateRecommendation(id, draft.role(), draft.reason()));
	}

	@Transactional
	public void approve(EntityCandidate candidate, String role, boolean chatEnabled) {
		Long knowledgeEntityId = mapper.findKnowledgeEntityIdByCandidateId(candidate.getId());
		if (knowledgeEntityId == null) throw new CharacterReviewException("KG 구축 후 후보를 승인할 수 있습니다.");
		if (chatEnabled) {
			boolean anotherEnabled = mapper.findCharactersByBookId(candidate.getBookId()).stream()
					.anyMatch(character -> character.chatEnabled() && !character.candidateId().equals(candidate.getId()));
			if (anotherEnabled) throw new CharacterReviewException("책마다 대화 가능 인물은 한 명만 선택할 수 있습니다.");
		}
		mapper.updateCandidateDecision(candidate.getId(), "APPROVED", null);
		mapper.updateKnowledgeEntityStatus(candidate.getId(), "APPROVED");
		mapper.upsertCharacter(candidate.getBookId(), candidate.getId(), knowledgeEntityId,
				candidate.getCanonicalName(), role, chatEnabled);
		mapper.deleteAliasesByCandidateId(candidate.getId());
		mapper.insertAliasesFromMentions(candidate.getId());
		mapper.refreshBookStatus(candidate.getBookId());
	}

	@Transactional
	public void reject(EntityCandidate candidate) {
		mapper.deleteCharacterByCandidateId(candidate.getId());
		mapper.deleteKnowledgeEntityByCandidateId(candidate.getId());
		mapper.updateCandidateDecision(candidate.getId(), "REJECTED", null);
		mapper.refreshBookStatus(candidate.getBookId());
	}

	@Transactional
	public void merge(EntityCandidate source, EntityCandidate target) {
		if (mapper.findKnowledgeEntityIdByCandidateId(source.getId()) == null
				|| mapper.findKnowledgeEntityIdByCandidateId(target.getId()) == null) {
			throw new CharacterReviewException("KG 구축 후 후보를 병합할 수 있습니다.");
		}
		mapper.moveMentions(source.getId(), target.getId());
		mapper.deleteSourceMentions(source.getId());
		mapper.moveFacts(source.getId(), target.getId());
		mapper.mergeKnowledgeGraph(source.getId(), target.getId());
		mapper.deleteCharacterByCandidateId(source.getId());
		mapper.deleteKnowledgeEntityByCandidateId(source.getId());
		mapper.updateCandidateDecision(source.getId(), "MERGED", target.getId());
		mapper.refreshBookStatus(source.getBookId());
	}
}

record RecommendationDraft(String role, String reason) {}
