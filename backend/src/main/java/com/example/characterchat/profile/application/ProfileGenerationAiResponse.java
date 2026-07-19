package com.example.characterchat.profile.application;

import java.util.List;

public class ProfileGenerationAiResponse {
	public String roleDescription; public String appearance; public String personality; public String values;
	public String goals; public String speechStyle; public String majorExperiences; public String attitudesTowardOthers;
	public String knownFacts; public String systemPrompt; public List<Evidence> evidence;
	public static class Evidence {
		public String profileField; public int sourceOrder; public int pageNumber; public int imageOrder;
		public String sourceType; public String inferenceType; public String description; public double confidence;
	}
}
