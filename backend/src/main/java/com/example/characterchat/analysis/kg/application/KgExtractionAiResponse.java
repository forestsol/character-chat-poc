package com.example.characterchat.analysis.kg.application;

import java.util.List;

public class KgExtractionAiResponse {
	public List<Event> events;
	public List<Relation> relations;

	public static class Event {
		public String name; public String description; public int sequenceOrder; public double confidence;
		public Evidence evidence; public List<Participant> participants;
	}
	public static class Participant {
		public String candidateName; public String role; public Evidence evidence;
	}
	public static class Relation {
		public String sourceCandidateName; public String relationType; public String targetCandidateName;
		public String description; public double confidence; public Evidence evidence;
	}
	public static class Evidence {
		public int sourceOrder; public int pageNumber; public int imageOrder;
	}
}
