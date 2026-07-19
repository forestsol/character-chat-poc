package com.example.characterchat.rag.application;

import com.example.characterchat.ai.EmbeddingClient;
import com.example.characterchat.book.domain.Book;
import com.example.characterchat.book.persistence.BookMapper;
import com.example.characterchat.common.exception.BookNotFoundException;
import com.example.characterchat.rag.api.RagIndexResponse;
import com.example.characterchat.rag.api.RagSearchResponse;
import com.example.characterchat.rag.domain.RagDocument;
import com.example.characterchat.rag.domain.RagParagraph;
import com.example.characterchat.rag.domain.RagSearchHit;
import com.example.characterchat.rag.persistence.RagMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class RagService {
	private static final String STRATEGY_VERSION = "paragraph-v1";
	private final BookMapper bookMapper;
	private final RagMapper ragMapper;
	private final EmbeddingClient embeddingClient;
	private final RagSearchStrategy searchStrategy;
	private final RagProperties properties;
	private final RagWriter writer;

	public RagService(BookMapper bookMapper, RagMapper ragMapper, EmbeddingClient embeddingClient,
	                  RagSearchStrategy searchStrategy, RagProperties properties, RagWriter writer) {
		this.bookMapper = bookMapper;
		this.ragMapper = ragMapper;
		this.embeddingClient = embeddingClient;
		this.searchStrategy = searchStrategy;
		this.properties = properties;
		this.writer = writer;
	}

	public RagIndexResponse index(Long bookId) {
		requireBook(bookId);
		List<RagParagraph> paragraphs = ragMapper.findParagraphsByBookId(bookId);
		if (paragraphs.isEmpty()) throw new RagException("RAG로 색인할 원문 문단이 없습니다.");
		List<RagDocument> documents = new ArrayList<>();
		int batchSize = positive(properties.getEmbeddingBatchSize(), "embeddingBatchSize");
		for (int start = 0; start < paragraphs.size(); start += batchSize) {
			List<RagParagraph> batch = paragraphs.subList(start, Math.min(start + batchSize, paragraphs.size()));
			List<List<Float>> embeddings = embeddingClient.embed(batch.stream().map(RagParagraph::content).toList());
			if (embeddings.size() != batch.size()) throw new RagException("임베딩 응답 개수가 문단 개수와 다릅니다.");
			for (int i = 0; i < batch.size(); i++) documents.add(toDocument(bookId, batch.get(i), embeddings.get(i)));
		}
		writer.replace(bookId, documents);
		return new RagIndexResponse(bookId, documents.size(), properties.getEmbeddingModel(),
				properties.getEmbeddingDimensions(), STRATEGY_VERSION);
	}

	public RagSearchResponse search(Long bookId, String query) {
		requireBook(bookId);
		if (query == null || query.isBlank()) throw new RagException("검색 질문은 비어 있을 수 없습니다.");
		if (ragMapper.countByBookId(bookId) == 0) throw new RagException("먼저 도서의 RAG 색인을 생성해야 합니다.");
		int topK = positive(properties.getTopK(), "topK");
		int window = nonNegative(properties.getContextWindow(), "contextWindow");
		List<Float> queryEmbedding = embeddingClient.embed(query.trim());
		validateEmbedding(queryEmbedding);
		List<RagSearchHit> hits = searchStrategy.search(bookId, queryEmbedding, topK);
		List<RagParagraph> paragraphs = ragMapper.findParagraphsByBookId(bookId);
		List<MutableRange> ranges = hits.stream()
				.map(hit -> new MutableRange(hit.sourceOrder() - window, hit.sourceOrder() + window, hit.score()))
				.sorted(Comparator.comparingInt(range -> range.start)).toList();
		List<MutableRange> merged = merge(ranges);
		List<RagSearchResponse.Range> responseRanges = merged.stream().map(range -> toResponse(range, paragraphs)).toList();
		return new RagSearchResponse(bookId, query.trim(), topK, window, responseRanges);
	}

	private RagDocument toDocument(Long bookId, RagParagraph paragraph, List<Float> embedding) {
		validateEmbedding(embedding);
		RagDocument document = new RagDocument();
		document.setBookId(bookId); document.setDocumentType("PARAGRAPH"); document.setReferenceId(paragraph.id());
		document.setContent(paragraph.content()); document.setSourceOrderStart(paragraph.sourceOrder());
		document.setSourceOrderEnd(paragraph.sourceOrder()); document.setPageNumberStart(paragraph.pageNumber());
		document.setPageNumberEnd(paragraph.pageNumber()); document.setEmbedding(VectorLiteral.format(embedding));
		document.setEmbeddingModel(properties.getEmbeddingModel()); document.setStrategyVersion(STRATEGY_VERSION);
		return document;
	}

	private void validateEmbedding(List<Float> embedding) {
		if (embedding == null || embedding.size() != properties.getEmbeddingDimensions())
			throw new RagException("임베딩 차원이 설정값과 다릅니다: " + properties.getEmbeddingDimensions());
		if (embedding.stream().anyMatch(value -> value == null || !Float.isFinite(value)))
			throw new RagException("임베딩에 유효하지 않은 값이 포함되어 있습니다.");
	}

	private List<MutableRange> merge(List<MutableRange> sorted) {
		List<MutableRange> merged = new ArrayList<>();
		for (MutableRange current : sorted) {
			if (merged.isEmpty() || current.start > merged.get(merged.size() - 1).end + 1) merged.add(new MutableRange(current.start, current.end, current.score));
			else { MutableRange last = merged.get(merged.size() - 1); last.end = Math.max(last.end, current.end); last.score = Math.max(last.score, current.score); }
		}
		return merged;
	}

	private RagSearchResponse.Range toResponse(MutableRange range, List<RagParagraph> all) {
		List<RagParagraph> selected = all.stream().filter(p -> p.sourceOrder() >= range.start && p.sourceOrder() <= range.end).toList();
		if (selected.isEmpty()) throw new RagException("검색 범위에 해당하는 원문 문단이 없습니다.");
		List<RagSearchResponse.Paragraph> paragraphs = selected.stream()
				.map(p -> new RagSearchResponse.Paragraph(p.id(), p.sourceOrder(), p.pageNumber(), p.content())).toList();
		return new RagSearchResponse.Range(selected.get(0).sourceOrder(), selected.get(selected.size() - 1).sourceOrder(),
				selected.get(0).pageNumber(), selected.get(selected.size() - 1).pageNumber(), range.score, paragraphs);
	}

	private Book requireBook(Long bookId) {
		Book book = bookMapper.findBookById(bookId);
		if (book == null) throw new BookNotFoundException(bookId);
		return book;
	}
	private int positive(int value, String name) { if (value <= 0) throw new RagException(name + "는 1 이상이어야 합니다."); return value; }
	private int nonNegative(int value, String name) { if (value < 0) throw new RagException(name + "는 0 이상이어야 합니다."); return value; }
	private static final class MutableRange { int start; int end; double score; MutableRange(int start, int end, double score) { this.start=start; this.end=end; this.score=score; } }
}
