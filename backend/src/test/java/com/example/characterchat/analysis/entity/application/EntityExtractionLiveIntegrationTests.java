package com.example.characterchat.analysis.entity.application;

import com.example.characterchat.analysis.entity.api.EntityCandidateResponse;
import com.example.characterchat.book.application.BookService;
import com.example.characterchat.book.persistence.BookMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_OPENAI_LIVE_TESTS", matches = "true")
@SpringBootTest(properties = {
		"ai.provider=openai",
		"ai.entity-extraction.batch-size=100"
})
class EntityExtractionLiveIntegrationTests {
	private static final String BOOK_KEY = "alice-demo";

	@Autowired BookService bookService;
	@Autowired BookMapper bookMapper;
	@Autowired EntityExtractionService extractionService;

	@BeforeEach
	@AfterEach
	void clean() { bookMapper.deleteBookByBookKey(BOOK_KEY); }

	@Test
	void 실제_책에서_근거가_연결된_개체_후보를_추출한다() {
		Long bookId = bookService.importBook(BOOK_KEY).id();

		List<EntityCandidateResponse> candidates = extractionService.extract(bookId);

		assertThat(candidates).isNotEmpty();
		assertThat(candidates).anyMatch(candidate -> candidate.entityType().equals("CHARACTER"));
		assertThat(candidates).allMatch(candidate -> !candidate.mentions().isEmpty());
	}
}
