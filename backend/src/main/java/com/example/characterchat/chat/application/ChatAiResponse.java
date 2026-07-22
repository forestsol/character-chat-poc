package com.example.characterchat.chat.application;

import java.util.List;

public class ChatAiResponse {
	public String answer;
	public boolean supported;
	public List<Long> usedParagraphIds;
	public List<Long> usedRelationIds;
	public List<Long> usedProfileEvidenceIds;
}
