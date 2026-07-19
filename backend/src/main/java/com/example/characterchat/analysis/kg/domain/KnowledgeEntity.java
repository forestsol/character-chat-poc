package com.example.characterchat.analysis.kg.domain;

public class KnowledgeEntity {
	private Long id; private Long bookId; private String entityType; private String referenceType; private Long referenceId;
	private String name; private String description; private String reviewStatus;
	public Long getId() { return id; } public void setId(Long id) { this.id = id; }
	public Long getBookId() { return bookId; } public void setBookId(Long bookId) { this.bookId = bookId; }
	public String getEntityType() { return entityType; } public void setEntityType(String value) { this.entityType = value; }
	public String getReferenceType() { return referenceType; } public void setReferenceType(String value) { this.referenceType = value; }
	public Long getReferenceId() { return referenceId; } public void setReferenceId(Long value) { this.referenceId = value; }
	public String getName() { return name; } public void setName(String value) { this.name = value; }
	public String getDescription() { return description; } public void setDescription(String value) { this.description = value; }
	public String getReviewStatus() { return reviewStatus; } public void setReviewStatus(String value) { this.reviewStatus = value; }
}
