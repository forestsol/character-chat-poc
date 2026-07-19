package com.example.characterchat.analysis.image.domain;

public class ExtractedFact {
	private Long id;
	private Long bookId;
	private String factType;
	private Long subjectCandidateId;
	private String value;
	private String sourceType;
	private String status;
	private double confidence;
	private Long paragraphId;
	private Long imageId;
	private String description;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public Long getBookId() { return bookId; }
	public void setBookId(Long bookId) { this.bookId = bookId; }
	public String getFactType() { return factType; }
	public void setFactType(String factType) { this.factType = factType; }
	public Long getSubjectCandidateId() { return subjectCandidateId; }
	public void setSubjectCandidateId(Long subjectCandidateId) { this.subjectCandidateId = subjectCandidateId; }
	public String getValue() { return value; }
	public void setValue(String value) { this.value = value; }
	public String getSourceType() { return sourceType; }
	public void setSourceType(String sourceType) { this.sourceType = sourceType; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public double getConfidence() { return confidence; }
	public void setConfidence(double confidence) { this.confidence = confidence; }
	public Long getParagraphId() { return paragraphId; }
	public void setParagraphId(Long paragraphId) { this.paragraphId = paragraphId; }
	public Long getImageId() { return imageId; }
	public void setImageId(Long imageId) { this.imageId = imageId; }
	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }
}
