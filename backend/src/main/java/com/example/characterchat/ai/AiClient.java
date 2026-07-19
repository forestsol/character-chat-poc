package com.example.characterchat.ai;

public interface AiClient {

	String generateText(AiTextRequest request);

	<T> T generateStructured(AiTextRequest request, Class<T> responseType);

	String analyzeImages(AiMultimodalRequest request);
}
