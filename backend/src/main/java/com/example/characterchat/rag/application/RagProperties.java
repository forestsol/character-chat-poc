package com.example.characterchat.rag.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "ai.rag")
public class RagProperties {

	private String embeddingModel = "text-embedding-3-small";
	private int embeddingDimensions = 1536;
	private int topK = 3;
	private int contextWindow = 2;
	private int embeddingBatchSize = 100;

	public String getEmbeddingModel() { return embeddingModel; }
	public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
	public int getEmbeddingDimensions() { return embeddingDimensions; }
	public void setEmbeddingDimensions(int embeddingDimensions) { this.embeddingDimensions = embeddingDimensions; }
	public int getTopK() { return topK; }
	public void setTopK(int topK) { this.topK = topK; }
	public int getContextWindow() { return contextWindow; }
	public void setContextWindow(int contextWindow) { this.contextWindow = contextWindow; }
	public int getEmbeddingBatchSize() { return embeddingBatchSize; }
	public void setEmbeddingBatchSize(int embeddingBatchSize) { this.embeddingBatchSize = embeddingBatchSize; }
}
