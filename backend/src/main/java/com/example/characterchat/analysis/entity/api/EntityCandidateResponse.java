package com.example.characterchat.analysis.entity.api;

import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityMention;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record EntityCandidateResponse(
		Long id,
		String entityType,
		String canonicalName,
		String description,
		double confidence,
		String reviewStatus,
		List<String> aliases,
		List<Mention> mentions
) {
	public static List<EntityCandidateResponse> from(List<EntityCandidate> candidates, List<EntityMention> mentions) {
		Map<Long, List<EntityMention>> byCandidate = mentions.stream()
				.collect(Collectors.groupingBy(EntityMention::entityCandidateId));
		return candidates.stream().map(candidate -> {
			List<EntityMention> candidateMentions = byCandidate.getOrDefault(candidate.getId(), List.of());
			List<String> aliases = candidateMentions.stream().map(EntityMention::mentionText)
					.filter(mention -> !mention.equalsIgnoreCase(candidate.getCanonicalName()))
					.distinct().toList();
			return new EntityCandidateResponse(
					candidate.getId(), candidate.getEntityType().name(), candidate.getCanonicalName(),
					candidate.getDescription(), candidate.getConfidence(), candidate.getReviewStatus(), aliases,
					candidateMentions.stream().map(Mention::from).toList()
			);
		}).toList();
	}

	public record Mention(Long id, Long paragraphId, Long imageId, String mentionText, String sourceType, double confidence) {
		static Mention from(EntityMention mention) {
			return new Mention(mention.id(), mention.paragraphId(), mention.imageId(), mention.mentionText(), mention.sourceType(), mention.confidence());
		}
	}
}
