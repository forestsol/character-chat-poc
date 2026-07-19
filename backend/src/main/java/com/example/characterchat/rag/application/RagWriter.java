package com.example.characterchat.rag.application;

import com.example.characterchat.rag.domain.RagDocument;
import com.example.characterchat.rag.persistence.RagMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class RagWriter {
	private final RagMapper mapper;
	public RagWriter(RagMapper mapper) { this.mapper = mapper; }

	@Transactional
	public void replace(Long bookId, List<RagDocument> documents) {
		mapper.deleteByBookId(bookId);
		documents.forEach(mapper::insert);
		mapper.updateBookStatus(bookId, "RAG_INDEXED");
	}
}

