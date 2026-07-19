package com.example.characterchat.analysis.image.application;

import java.util.List;

public class ImageAnalysisAiResponse {
	public List<VisualEntity> entities;
	public List<VisualFact> facts;

	public static class VisualEntity {
		public int imageOrder;
		public String entityType;
		public String observedName;
		public String matchedCandidateName;
		public String description;
		public double confidence;
	}

	public static class VisualFact {
		public int imageOrder;
		public String factType;
		public String subjectName;
		public String value;
		public String sourceType;
		public String status;
		public double confidence;
		public String description;
	}
}
