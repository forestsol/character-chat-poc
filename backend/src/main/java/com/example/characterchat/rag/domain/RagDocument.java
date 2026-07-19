package com.example.characterchat.rag.domain;

public class RagDocument {
	private Long id;
	private Long bookId;
	private String documentType;
	private Long referenceId;
	private String content;
	private int sourceOrderStart;
	private int sourceOrderEnd;
	private int pageNumberStart;
	private int pageNumberEnd;
	private String embedding;
	private String embeddingModel;
	private String strategyVersion;

	public Long getId() { return id; }
	public Long getBookId() { return bookId; }
	public void setBookId(Long bookId) { this.bookId = bookId; }
	public String getDocumentType() { return documentType; }
	public void setDocumentType(String documentType) { this.documentType = documentType; }
	public Long getReferenceId() { return referenceId; }
	public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
	public String getContent() { return content; }
	public void setContent(String content) { this.content = content; }
	public int getSourceOrderStart() { return sourceOrderStart; }
	public void setSourceOrderStart(int sourceOrderStart) { this.sourceOrderStart = sourceOrderStart; }
	public int getSourceOrderEnd() { return sourceOrderEnd; }
	public void setSourceOrderEnd(int sourceOrderEnd) { this.sourceOrderEnd = sourceOrderEnd; }
	public int getPageNumberStart() { return pageNumberStart; }
	public void setPageNumberStart(int pageNumberStart) { this.pageNumberStart = pageNumberStart; }
	public int getPageNumberEnd() { return pageNumberEnd; }
	public void setPageNumberEnd(int pageNumberEnd) { this.pageNumberEnd = pageNumberEnd; }
	public String getEmbedding() { return embedding; }
	public void setEmbedding(String embedding) { this.embedding = embedding; }
	public String getEmbeddingModel() { return embeddingModel; }
	public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
	public String getStrategyVersion() { return strategyVersion; }
	public void setStrategyVersion(String strategyVersion) { this.strategyVersion = strategyVersion; }
}

