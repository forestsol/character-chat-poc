package com.example.characterchat.ai.fake;

import com.example.characterchat.ai.AiClient;
import com.example.characterchat.ai.AiClientException;
import com.example.characterchat.ai.AiMultimodalRequest;
import com.example.characterchat.ai.AiTextRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "fake", matchIfMissing = true)
public class FakeAiClient implements AiClient {

	private final Deque<String> textResponses = new ArrayDeque<>();
	private final Deque<String> imageResponses = new ArrayDeque<>();
	private final Map<Class<?>, Deque<Object>> structuredResponses = new HashMap<>();

	public void enqueueTextResponse(String response) {
		textResponses.addLast(response);
	}

	public void enqueueImageResponse(String response) {
		imageResponses.addLast(response);
	}

	public <T> void enqueueStructuredResponse(Class<T> responseType, T response) {
		structuredResponses.computeIfAbsent(responseType, ignored -> new ArrayDeque<>()).addLast(response);
	}

	@Override
	public String generateText(AiTextRequest request) {
		return poll(textResponses, "텍스트");
	}

	@Override
	public <T> T generateStructured(AiTextRequest request, Class<T> responseType) {
		Deque<Object> responses = structuredResponses.get(responseType);
		if (responses == null || responses.isEmpty()) {
			throw new AiClientException("등록된 Fake 구조화 응답이 없습니다: " + responseType.getName());
		}
		return responseType.cast(responses.removeFirst());
	}

	@Override
	public String analyzeImages(AiMultimodalRequest request) {
		return poll(imageResponses, "이미지");
	}

	public void clear() {
		textResponses.clear();
		imageResponses.clear();
		structuredResponses.clear();
	}

	private String poll(Deque<String> responses, String type) {
		if (responses.isEmpty()) {
			throw new AiClientException("등록된 Fake " + type + " 응답이 없습니다.");
		}
		return responses.removeFirst();
	}
}
