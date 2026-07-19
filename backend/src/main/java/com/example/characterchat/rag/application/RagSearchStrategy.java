package com.example.characterchat.rag.application;

import com.example.characterchat.rag.domain.RagSearchHit;

import java.util.List;

public interface RagSearchStrategy {
	List<RagSearchHit> search(Long bookId, List<Float> queryEmbedding, int topK);
}

