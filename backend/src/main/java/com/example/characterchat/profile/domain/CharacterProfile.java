package com.example.characterchat.profile.domain;

public class CharacterProfile {
	private Long id; private Long characterId; private String storyPoint; private String roleDescription;
	private String appearance; private String personality; private String values; private String goals;
	private String speechStyle; private String majorExperiences; private String attitudesTowardOthers;
	private String knownFacts; private String systemPrompt;
	public Long getId() { return id; } public void setId(Long v) { id=v; }
	public Long getCharacterId() { return characterId; } public void setCharacterId(Long v) { characterId=v; }
	public String getStoryPoint() { return storyPoint; } public void setStoryPoint(String v) { storyPoint=v; }
	public String getRoleDescription() { return roleDescription; } public void setRoleDescription(String v) { roleDescription=v; }
	public String getAppearance() { return appearance; } public void setAppearance(String v) { appearance=v; }
	public String getPersonality() { return personality; } public void setPersonality(String v) { personality=v; }
	public String getValues() { return values; } public void setValues(String v) { values=v; }
	public String getGoals() { return goals; } public void setGoals(String v) { goals=v; }
	public String getSpeechStyle() { return speechStyle; } public void setSpeechStyle(String v) { speechStyle=v; }
	public String getMajorExperiences() { return majorExperiences; } public void setMajorExperiences(String v) { majorExperiences=v; }
	public String getAttitudesTowardOthers() { return attitudesTowardOthers; } public void setAttitudesTowardOthers(String v) { attitudesTowardOthers=v; }
	public String getKnownFacts() { return knownFacts; } public void setKnownFacts(String v) { knownFacts=v; }
	public String getSystemPrompt() { return systemPrompt; } public void setSystemPrompt(String v) { systemPrompt=v; }
}
