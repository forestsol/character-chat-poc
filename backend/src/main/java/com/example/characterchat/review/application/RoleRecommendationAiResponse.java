package com.example.characterchat.review.application;

import java.util.List;

public class RoleRecommendationAiResponse {
	public List<Recommendation> recommendations;
	public static class Recommendation { public String candidateName; public String narrativeRole; public String reason; }
}
