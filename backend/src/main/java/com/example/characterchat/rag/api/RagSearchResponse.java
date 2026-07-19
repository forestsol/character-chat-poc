package com.example.characterchat.rag.api;

import java.util.List;

public record RagSearchResponse(Long bookId, String query, int topK, int contextWindow, List<Range> ranges) {
	public record Range(int sourceOrderStart, int sourceOrderEnd, int pageNumberStart, int pageNumberEnd,
	                    double score, List<Paragraph> paragraphs) { }
	public record Paragraph(Long paragraphId, int sourceOrder, int pageNumber, String content) { }
}

