package com.example.characterchat.ai;

import java.util.List;

public interface EmbeddingClient {

	List<List<Float>> embed(List<String> inputs);

	default List<Float> embed(String input) {
		return embed(List.of(input)).get(0);
	}
}
