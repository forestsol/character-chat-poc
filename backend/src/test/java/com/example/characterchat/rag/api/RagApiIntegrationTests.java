package com.example.characterchat.rag.api;

import com.example.characterchat.book.application.BookService;
import com.example.characterchat.book.persistence.BookMapper;
import com.example.characterchat.rag.application.RagService;
import com.example.characterchat.rag.domain.RagParagraph;
import com.example.characterchat.rag.persistence.RagMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RagApiIntegrationTests {
	private static final String BOOK_KEY = "alice-demo";
	@Autowired MockMvc mockMvc;
	@Autowired BookService bookService;
	@Autowired BookMapper bookMapper;
	@Autowired RagMapper ragMapper;
	@Autowired RagService ragService;

	@BeforeEach @AfterEach void clean() { bookMapper.deleteBookByBookKey(BOOK_KEY); }

	@Test
	void 원문_문단을_색인하고_질문과_가까운_범위를_검색한다() throws Exception {
		Long bookId = bookService.importBook(BOOK_KEY).id();
		List<RagParagraph> source = ragMapper.findParagraphsByBookId(bookId);

		mockMvc.perform(post("/api/books/{bookId}/rag/index", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.documentCount").value(source.size()))
				.andExpect(jsonPath("$.embeddingModel").value("text-embedding-3-small"))
				.andExpect(jsonPath("$.embeddingDimensions").value(1536));

		RagSearchResponse response = ragService.search(bookId, source.get(0).content());
		assertThat(response.ranges()).isNotEmpty();
		assertThat(response.ranges()).allSatisfy(range -> {
			assertThat(range.paragraphs()).isNotEmpty();
			assertThat(range.sourceOrderStart()).isLessThanOrEqualTo(range.sourceOrderEnd());
			assertThat(range.pageNumberStart()).isPositive();
		});
		for (int i = 1; i < response.ranges().size(); i++) {
			assertThat(response.ranges().get(i).sourceOrderStart())
					.isGreaterThan(response.ranges().get(i - 1).sourceOrderEnd() + 1);
		}

		mockMvc.perform(get("/api/books/{bookId}/rag/search", bookId).param("query", source.get(0).content()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.topK").value(3))
				.andExpect(jsonPath("$.contextWindow").value(2))
				.andExpect(jsonPath("$.ranges[0].paragraphs[0].sourceOrder").isNumber())
				.andExpect(jsonPath("$.ranges[0].paragraphs[0].pageNumber").isNumber());
		mockMvc.perform(get("/api/books/{bookId}", bookId))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RAG_INDEXED"));
	}

	@Test
	void 색인하지_않은_도서는_검색할_수_없다() throws Exception {
		Long bookId = bookService.importBook(BOOK_KEY).id();
		mockMvc.perform(get("/api/books/{bookId}/rag/search", bookId).param("query", "토끼는 어디로 갔어?"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("색인")));
	}
}

