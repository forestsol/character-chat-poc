package com.example.characterchat.book.api;

import com.example.characterchat.book.persistence.BookMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookApiIntegrationTests {

	private static final String BOOK_KEY = "alice-demo";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	BookMapper bookMapper;

	@BeforeEach
	@AfterEach
	void cleanBook() {
		bookMapper.deleteBookByBookKey(BOOK_KEY);
	}

	@Test
	void importsAndReadsAliceDemo() throws Exception {
		String response = mockMvc.perform(post("/api/books/import")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"bookDirectory\":\"alice-demo\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.bookKey").value(BOOK_KEY))
				.andExpect(jsonPath("$.status").value("IMPORTED"))
				.andExpect(jsonPath("$.pages.length()").value(10))
				.andExpect(jsonPath("$.pages[0].pageNumber").value(1))
				.andExpect(jsonPath("$.pages[9].pageNumber").value(10))
				.andExpect(jsonPath("$.pages[0].paragraphs[0].sourceOrder").value(1))
				.andExpect(jsonPath("$.pages[9].paragraphs[4].sourceOrder").value(38))
				.andExpect(jsonPath("$.pages[9].images[0].filePath").value("alice-demo/images/page-010-01.png"))
				.andReturn()
				.getResponse()
				.getContentAsString();

		long bookId = new ObjectMapperHolder().readId(response);

		mockMvc.perform(get("/api/books"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].bookKey").value(hasItem(BOOK_KEY)));

		mockMvc.perform(get("/api/books/{bookId}", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pages.length()").value(10));
	}

	@Test
	void rejectsDuplicateBookKey() throws Exception {
		mockMvc.perform(post("/api/books/import")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"bookDirectory\":\"alice-demo\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/books/import")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"bookDirectory\":\"alice-demo\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	@Test
	void returnsNotFoundForUnknownBook() throws Exception {
		mockMvc.perform(get("/api/books/{bookId}", Long.MAX_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void rejectsInvalidInputWithoutSavingBook() throws Exception {
		mockMvc.perform(post("/api/books/import")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"bookDirectory\":\"missing-book\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400));

		mockMvc.perform(get("/api/books"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].bookKey").value(not(hasItem("missing-book"))));
	}

	private static class ObjectMapperHolder {
		private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

		long readId(String json) throws Exception {
			return objectMapper.readTree(json).get("id").asLong();
		}
	}
}
