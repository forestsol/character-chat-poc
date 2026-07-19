package com.example.characterchat.analysis.entity.application;

public class EntityExtractionException extends RuntimeException {
	public EntityExtractionException(String message) { super(message); }
	public EntityExtractionException(String message, Throwable cause) { super(message, cause); }
}
