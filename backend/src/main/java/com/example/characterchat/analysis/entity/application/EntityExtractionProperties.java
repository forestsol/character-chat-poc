package com.example.characterchat.analysis.entity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai.entity-extraction")
public class EntityExtractionProperties {
	private int batchSize = 20;

	public int getBatchSize() { return batchSize; }
	public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
}
