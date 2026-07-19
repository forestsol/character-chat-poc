package com.example.characterchat.analysis.kg.domain;

public class StoryEvent {
	private Long id; private Long bookId; private String name; private String description; private Integer sequenceOrder;
	private double confidence; private String reviewStatus; private Long evidenceParagraphId; private Long evidenceImageId;
	public Long getId() { return id; } public void setId(Long id) { this.id = id; }
	public Long getBookId() { return bookId; } public void setBookId(Long bookId) { this.bookId = bookId; }
	public String getName() { return name; } public void setName(String name) { this.name = name; }
	public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
	public Integer getSequenceOrder() { return sequenceOrder; } public void setSequenceOrder(Integer sequenceOrder) { this.sequenceOrder = sequenceOrder; }
	public double getConfidence() { return confidence; } public void setConfidence(double confidence) { this.confidence = confidence; }
	public String getReviewStatus() { return reviewStatus; } public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
	public Long getEvidenceParagraphId() { return evidenceParagraphId; } public void setEvidenceParagraphId(Long value) { this.evidenceParagraphId = value; }
	public Long getEvidenceImageId() { return evidenceImageId; } public void setEvidenceImageId(Long value) { this.evidenceImageId = value; }
}
