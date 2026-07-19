package com.example.characterchat.rag.application;

import com.example.characterchat.rag.domain.RagSearchHit;
import com.example.characterchat.rag.persistence.RagMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PgVectorExactSearchStrategy implements RagSearchStrategy {
	private final RagMapper mapper;
	public PgVectorExactSearchStrategy(RagMapper mapper) { this.mapper = mapper; }
	@Override
	public List<RagSearchHit> search(Long bookId, List<Float> queryEmbedding, int topK) {
		return mapper.search(bookId, VectorLiteral.format(queryEmbedding), topK);
	}
}

