package com.example.characterchat.analysis.kg.application;

import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityType;
import com.example.characterchat.analysis.entity.persistence.EntityCandidateMapper;
import com.example.characterchat.analysis.kg.api.KnowledgeGraphResponse;
import com.example.characterchat.book.application.BookService;
import com.example.characterchat.book.persistence.BookMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_OPENAI_LIVE_TESTS", matches = "true")
@SpringBootTest(properties = "ai.provider=openai")
class KnowledgeGraphLiveIntegrationTests {
	private static final String BOOK_KEY = "alice-demo";
	@Autowired BookService bookService; @Autowired BookMapper bookMapper;
	@Autowired EntityCandidateMapper candidateMapper; @Autowired KnowledgeGraphService graphService;

	@BeforeEach @AfterEach void clean() { bookMapper.deleteBookByBookKey(BOOK_KEY); }

	@Test
	void 실제_원문에서_사건과_직접_관계를_KG로_구축한다() {
		Long bookId = bookService.importBook(BOOK_KEY).id();
		insert(bookId, "앨리스", "이야기의 주인공");
		insert(bookId, "토끼", "시계를 보며 달리는 토끼");
		insert(bookId, "모자 장수", "다과회에 참여하는 인물");
		insert(bookId, "체셔 고양이", "나타났다 사라지는 고양이");

		KnowledgeGraphResponse graph = graphService.build(bookId);

		assertThat(graph.events()).isNotEmpty();
		assertThat(graph.relations()).isNotEmpty();
		assertThat(graph.entities()).anyMatch(entity -> entity.entityType().equals("EVENT"));
		assertThat(graph.events()).allMatch(event -> event.evidenceParagraphId() != null || event.evidenceImageId() != null);
	}

	private void insert(Long bookId, String name, String description) {
		candidateMapper.insertCandidate(new EntityCandidate(bookId, EntityType.CHARACTER, name, description, 0.95));
	}
}
