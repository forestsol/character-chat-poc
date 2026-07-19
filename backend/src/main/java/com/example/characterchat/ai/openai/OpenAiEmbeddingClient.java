package com.example.characterchat.ai.openai;

import com.example.characterchat.ai.AiClientException;
import com.example.characterchat.ai.EmbeddingClient;
import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAiEmbeddingClient implements EmbeddingClient {

	private final OpenAIClient client;
	private final OpenAiProperties properties;

	public OpenAiEmbeddingClient(OpenAIClient client, OpenAiProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	@Override
	public List<List<Float>> embed(List<String> inputs) {
		try {
			EmbeddingCreateParams params = EmbeddingCreateParams.builder()
					.model(properties.getEmbeddingModel())
					.dimensions(properties.getEmbeddingDimensions())
					.inputOfArrayOfStrings(inputs)
					.build();
			CreateEmbeddingResponse response = client.embeddings().create(params);
			return response.data().stream()
					.sorted(Comparator.comparingLong(item -> item.index()))
					.map(item -> item.embedding())
					.toList();
		} catch (Exception exception) {
			throw new AiClientException("OpenAI 임베딩 생성에 실패했습니다.", exception);
		}
	}
}
