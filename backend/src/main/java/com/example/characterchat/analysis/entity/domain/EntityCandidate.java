package com.example.characterchat.analysis.entity.domain;

import java.time.OffsetDateTime;

public class EntityCandidate {
	private Long id;
	private Long bookId;
	private EntityType entityType;
	private String canonicalName;
	private String description;
	private double confidence;
	private String reviewStatus;
	private String originSource;
	private OffsetDateTime createdAt;

	public EntityCandidate() {}

	public EntityCandidate(Long bookId, EntityType entityType, String canonicalName, String description, double confidence) {
		this.bookId = bookId;
		this.entityType = entityType;
		this.canonicalName = canonicalName;
		this.description = description;
		this.confidence = confidence;
		this.reviewStatus = "PENDING";
		this.originSource = "TEXT";
	}

	public static EntityCandidate fromImage(Long bookId, EntityType entityType, String canonicalName,
			String description, double confidence) {
		EntityCandidate candidate = new EntityCandidate(bookId, entityType, canonicalName, description, confidence);
		candidate.originSource = "IMAGE";
		return candidate;
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public Long getBookId() { return bookId; }
	public void setBookId(Long bookId) { this.bookId = bookId; }
	public EntityType getEntityType() { return entityType; }
	public void setEntityType(EntityType entityType) { this.entityType = entityType; }
	public String getCanonicalName() { return canonicalName; }
	public void setCanonicalName(String canonicalName) { this.canonicalName = canonicalName; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
	public double getConfidence() { return confidence; }
	public void setConfidence(double confidence) { this.confidence = confidence; }
	public String getReviewStatus() { return reviewStatus; }
	public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
	public String getOriginSource() { return originSource; }
	public void setOriginSource(String originSource) { this.originSource = originSource; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
