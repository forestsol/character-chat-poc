package com.example.characterchat.ai.fake;

import com.example.characterchat.ai.EmbeddingClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "fake", matchIfMissing = true)
public class FakeEmbeddingClient implements EmbeddingClient {

	private static final int DIMENSIONS = 1536;

	@Override
	public List<List<Float>> embed(List<String> inputs) {
		return inputs.stream().map(this::deterministicEmbedding).toList();
	}

	private List<Float> deterministicEmbedding(String input) {
		float[] values = new float[DIMENSIONS];
		byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
		for (int i = 0; i < bytes.length; i++) {
			values[Math.floorMod((bytes[i] & 0xff) * 31 + i * 17, DIMENSIONS)] += 1.0f;
		}
		double norm = 0;
		for (float value : values) norm += value * value;
		norm = Math.sqrt(norm);
		List<Float> result = new ArrayList<>(DIMENSIONS);
		for (float value : values) result.add(norm == 0 ? 0.0f : (float) (value / norm));
		return result;
	}
}

