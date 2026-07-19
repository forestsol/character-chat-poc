package com.example.characterchat.review.api;

public record CharacterReviewRequest(String decision, String narrativeRole, Boolean chatEnabled, Long mergeTargetCandidateId) {}
