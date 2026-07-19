package com.example.characterchat.analysis.image.application;

import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityType;
import com.example.characterchat.analysis.entity.persistence.EntityCandidateMapper;
import com.example.characterchat.analysis.image.api.ImageFactResponse;
import com.example.characterchat.book.application.BookService;
import com.example.characterchat.book.domain.BookPage;
import com.example.characterchat.book.persistence.BookMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_OPENAI_LIVE_TESTS", matches = "true")
@SpringBootTest(properties = "ai.provider=openai")
class ImageAnalysisLiveIntegrationTests {
	private static final String BOOK_KEY = "alice-demo";

	@Autowired BookService bookService;
	@Autowired BookMapper bookMapper;
	@Autowired EntityCandidateMapper candidateMapper;
	@Autowired ImageAnalysisService imageAnalysisService;
	@Autowired JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void clean() { bookMapper.deleteBookByBookKey(BOOK_KEY); }

	@Test
	void 실제_삽화를_페이지_원문과_후보와_함께_분석한다() {
		Long bookId = bookService.importBook(BOOK_KEY).id();
		BookPage pageTwo = bookMapper.findPagesByBookId(bookId).stream()
				.filter(page -> page.getPageNumber() == 2).findFirst().orElseThrow();
		jdbcTemplate.update("DELETE FROM book_image WHERE book_id = ? AND page_id <> ?", bookId, pageTwo.getId());
		EntityCandidate alice = new EntityCandidate(bookId, EntityType.CHARACTER, "Alice", "이야기의 주인공", 0.98);
		candidateMapper.insertCandidate(alice);

		List<ImageFactResponse> facts = imageAnalysisService.analyze(bookId);

		assertThat(facts).isNotEmpty();
		assertThat(facts).allMatch(fact -> fact.imageId() != null);
		assertThat(facts).allMatch(fact -> fact.confidence() >= 0 && fact.confidence() <= 1);
	}
}
