package com.example.characterchat.analysis.entity.application;

import java.util.List;

public class EntityExtractionAiResponse {
	public List<ExtractedEntity> entities;

	public static class ExtractedEntity {
		public String entityType;
		public String canonicalName;
		public List<String> aliases;
		public String description;
		public double confidence;
		public List<MentionEvidence> evidence;
	}

	public static class MentionEvidence {
		public int sourceOrder;
		public String mentionText;
	}
}
