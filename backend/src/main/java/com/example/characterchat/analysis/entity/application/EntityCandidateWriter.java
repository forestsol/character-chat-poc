package com.example.characterchat.analysis.entity.application;

import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityMention;
import com.example.characterchat.analysis.entity.persistence.EntityCandidateMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class EntityCandidateWriter {
	private final EntityCandidateMapper mapper;

	public EntityCandidateWriter(EntityCandidateMapper mapper) { this.mapper = mapper; }

	@Transactional
	public void replace(Long bookId, List<EntityCandidateDraft> drafts) {
		mapper.deleteCandidatesByBookId(bookId);
		for (EntityCandidateDraft draft : drafts) {
			EntityCandidate candidate = new EntityCandidate(bookId, draft.entityType(), draft.canonicalName(),
					draft.description(), draft.confidence());
			mapper.insertCandidate(candidate);
			for (MentionDraft mention : draft.mentions()) {
				mapper.insertMention(new EntityMention(null, candidate.getId(), mention.paragraphId(),
						mention.mentionText(), "TEXT", mention.confidence()));
			}
		}
		mapper.updateBookStatus(bookId, "TEXT_ENTITIES_EXTRACTED");
	}
}
