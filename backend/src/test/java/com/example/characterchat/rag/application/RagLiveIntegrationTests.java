package com.example.characterchat.rag.application;

import com.example.characterchat.book.application.BookService;
import com.example.characterchat.book.persistence.BookMapper;
import com.example.characterchat.rag.api.RagSearchResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_OPENAI_LIVE_TESTS", matches = "true")
@SpringBootTest(properties = "ai.provider=openai")
class RagLiveIntegrationTests {
	private static final String BOOK_KEY = "alice-demo";
	@Autowired BookService bookService;
	@Autowired BookMapper bookMapper;
	@Autowired RagService ragService;

	@BeforeEach @AfterEach void clean() { bookMapper.deleteBookByBookKey(BOOK_KEY); }

	@Test
	void 실제_OpenAI_임베딩으로_앨리스_원문을_검색한다() {
		Long bookId = bookService.importBook(BOOK_KEY).id();
		ragService.index(bookId);
		RagSearchResponse response = ragService.search(bookId, "앨리스가 흰 토끼를 따라간 이유는 무엇이야?");
		assertThat(response.ranges()).isNotEmpty();
		assertThat(response.ranges()).flatExtracting(RagSearchResponse.Range::paragraphs).isNotEmpty();
	}
}
