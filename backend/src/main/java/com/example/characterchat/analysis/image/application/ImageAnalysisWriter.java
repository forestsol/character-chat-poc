package com.example.characterchat.analysis.image.application;

import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityMention;
import com.example.characterchat.analysis.image.domain.ExtractedFact;
import com.example.characterchat.analysis.image.persistence.ImageAnalysisMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

@Component
public class ImageAnalysisWriter {
	private final ImageAnalysisMapper mapper;

	public ImageAnalysisWriter(ImageAnalysisMapper mapper) { this.mapper = mapper; }

	@Transactional
	public void replace(Long bookId, List<VisualEntityDraft> entities, List<VisualFactDraft> facts) {
		mapper.deleteImageFactsByBookId(bookId);
		mapper.deleteImageMentionsByBookId(bookId);
		mapper.deleteImageCandidatesByBookId(bookId);

		Map<String, Long> candidateIds = new LinkedHashMap<>();
		entities.stream().filter(entity -> entity.existingCandidateId() != null)
				.forEach(entity -> candidateIds.put(entity.subjectKey(), entity.existingCandidateId()));
		facts.stream().map(VisualFactDraft::subjectKey).filter(key -> key != null && key.startsWith("existing:"))
				.forEach(key -> candidateIds.putIfAbsent(key, Long.parseLong(key.substring("existing:".length()))));

		Map<String, VisualEntityDraft> newCandidates = new LinkedHashMap<>();
		entities.stream().filter(entity -> entity.existingCandidateId() == null)
				.forEach(entity -> newCandidates.merge(entity.subjectKey(), entity, (left, right) ->
						right.confidence() > left.confidence() ? right : left));
		for (VisualEntityDraft draft : newCandidates.values()) {
			EntityCandidate candidate = EntityCandidate.fromImage(bookId, draft.entityType(), draft.observedName(),
					draft.description(), draft.confidence());
			mapper.insertImageCandidate(candidate);
			candidateIds.put(draft.subjectKey(), candidate.getId());
		}

		Set<String> mentionKeys = new HashSet<>();
		for (VisualEntityDraft entity : entities) {
			String mentionKey = entity.subjectKey() + ":" + entity.imageId() + ":" + entity.observedName();
			if (mentionKeys.add(mentionKey)) {
				mapper.insertImageMention(new EntityMention(null, candidateIds.get(entity.subjectKey()), null,
						entity.imageId(), entity.observedName(), entity.sourceType(), entity.confidence()));
			}
		}

		for (VisualFactDraft draft : facts) {
			ExtractedFact fact = new ExtractedFact();
			fact.setBookId(bookId);
			fact.setFactType(draft.factType());
			fact.setSubjectCandidateId(draft.subjectKey() == null ? null : candidateIds.get(draft.subjectKey()));
			fact.setValue(draft.value());
			fact.setSourceType(draft.sourceType());
			fact.setStatus(draft.status());
			fact.setConfidence(draft.confidence());
			fact.setImageId(draft.imageId());
			fact.setDescription(draft.description());
			mapper.insertFact(fact);
		}
		mapper.updateBookStatus(bookId);
	}
}
