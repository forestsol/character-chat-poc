package com.example.characterchat.rag.domain;

public record RagSearchHit(Long documentId, int sourceOrder, double score) {
}

