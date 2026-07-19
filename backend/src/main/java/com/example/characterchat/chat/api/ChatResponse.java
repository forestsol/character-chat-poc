package com.example.characterchat.chat.api;

import com.example.characterchat.chat.domain.DirectKnowledgeRelation;
import com.example.characterchat.rag.api.RagSearchResponse;

import java.util.List;

public record ChatResponse(Long bookId, CharacterSummary character, String answer, boolean grounded, Debug debug) {
	public record CharacterSummary(Long id, String name, String narrativeRole, String storyPoint) { }
	public record Debug(List<Long> usedParagraphIds, List<Long> usedRelationIds,
	                    List<RagSearchResponse.Range> ragRanges,
	                    List<DirectKnowledgeRelation> directRelations) { }
}

