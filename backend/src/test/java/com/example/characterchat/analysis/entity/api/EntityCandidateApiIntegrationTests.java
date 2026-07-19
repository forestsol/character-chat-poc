package com.example.characterchat.analysis.entity.api;

import com.example.characterchat.ai.fake.FakeAiClient;
import com.example.characterchat.analysis.entity.application.EntityExtractionAiResponse;
import com.example.characterchat.book.persistence.BookMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "ai.entity-extraction.batch-size=20")
@AutoConfigureMockMvc
class EntityCandidateApiIntegrationTests {
	private static final String BOOK_KEY = "alice-demo";

	@Autowired MockMvc mockMvc;
	@Autowired BookMapper bookMapper;
	@Autowired FakeAiClient fakeAiClient;
	@Autowired ObjectMapper objectMapper;

	@BeforeEach
	@AfterEach
	void clean() {
		fakeAiClient.clear();
		bookMapper.deleteBookByBookKey(BOOK_KEY);
	}

	@Test
	void 책_문단에서_후보와_근거를_추출하고_조회한다() throws Exception {
		long bookId = importBook();
		fakeAiClient.enqueueStructuredResponse(EntityExtractionAiResponse.class,
				response("CHARACTER", "Alice", List.of("ALICE"), "이야기의 주인공", 0.98, 1, "ALICE"));
		fakeAiClient.enqueueStructuredResponse(EntityExtractionAiResponse.class,
				response("CHARACTER", "앨리스", List.of("Alice"), "여러 장소를 여행하는 주인공", 0.96, 21, "앨리스"));

		mockMvc.perform(post("/api/books/{bookId}/entity-candidates/extract", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].entityType").value("CHARACTER"))
				.andExpect(jsonPath("$[0].canonicalName").value("Alice"))
				.andExpect(jsonPath("$[0].reviewStatus").value("PENDING"))
				.andExpect(jsonPath("$[0].mentions[0].mentionText").value("ALICE"))
				.andExpect(jsonPath("$[0].mentions[0].sourceType").value("TEXT"))
				.andExpect(jsonPath("$[0].aliases[0]").value("앨리스"))
				.andExpect(jsonPath("$[0].mentions.length()").value(2));

		mockMvc.perform(get("/api/books/{bookId}/entity-candidates", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mockMvc.perform(get("/api/books/{bookId}", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("TEXT_ENTITIES_EXTRACTED"));
	}

	@Test
	void 잘못된_AI_근거는_저장하지_않고_502를_반환한다() throws Exception {
		long bookId = importBook();
		fakeAiClient.enqueueStructuredResponse(EntityExtractionAiResponse.class,
				response("CHARACTER", "Alice", List.of(), "주인공", 0.9, 999, "Alice"));

		mockMvc.perform(post("/api/books/{bookId}/entity-candidates/extract", bookId))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("sourceOrder")));

		mockMvc.perform(get("/api/books/{bookId}/entity-candidates", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void mentionText가_같은_배치의_다른_문단에_있으면_근거_문단을_교정한다() throws Exception {
		long bookId = importBook();
		fakeAiClient.enqueueStructuredResponse(EntityExtractionAiResponse.class,
				response("CHARACTER", "Alice", List.of(), "주인공", 0.9, 2, "ALICE"));
		EntityExtractionAiResponse emptyResponse = new EntityExtractionAiResponse();
		emptyResponse.entities = List.of();
		fakeAiClient.enqueueStructuredResponse(EntityExtractionAiResponse.class, emptyResponse);

		mockMvc.perform(post("/api/books/{bookId}/entity-candidates/extract", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].canonicalName").value("Alice"))
				.andExpect(jsonPath("$[0].mentions[0].mentionText").value("ALICE"));
	}

	@Test
	void 재분석이_실패하면_기존_후보를_유지한다() throws Exception {
		long bookId = importBook();
		fakeAiClient.enqueueStructuredResponse(EntityExtractionAiResponse.class,
				response("CHARACTER", "Alice", List.of(), "주인공", 0.9, 1, "ALICE"));
		EntityExtractionAiResponse emptyResponse = new EntityExtractionAiResponse();
		emptyResponse.entities = List.of();
		fakeAiClient.enqueueStructuredResponse(EntityExtractionAiResponse.class, emptyResponse);
		mockMvc.perform(post("/api/books/{bookId}/entity-candidates/extract", bookId))
				.andExpect(status().isOk());

		fakeAiClient.enqueueStructuredResponse(EntityExtractionAiResponse.class,
				response("PLACE", "없는 장소", List.of(), "잘못된 결과", 0.8, 999, "없는 장소"));
		mockMvc.perform(post("/api/books/{bookId}/entity-candidates/extract", bookId))
				.andExpect(status().isBadGateway());

		mockMvc.perform(get("/api/books/{bookId}/entity-candidates", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].canonicalName").value("Alice"));
	}

	@Test
	void 존재하지_않는_책의_후보_조회는_404를_반환한다() throws Exception {
		mockMvc.perform(get("/api/books/{bookId}/entity-candidates", Long.MAX_VALUE))
				.andExpect(status().isNotFound());
	}

	private long importBook() throws Exception {
		String json = mockMvc.perform(post("/api/books/import")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"bookDirectory\":\"alice-demo\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private EntityExtractionAiResponse response(String type, String name, List<String> aliases,
			String description, double confidence, int sourceOrder, String mentionText) {
		EntityExtractionAiResponse.MentionEvidence evidence = new EntityExtractionAiResponse.MentionEvidence();
		evidence.sourceOrder = sourceOrder;
		evidence.mentionText = mentionText;
		EntityExtractionAiResponse.ExtractedEntity entity = new EntityExtractionAiResponse.ExtractedEntity();
		entity.entityType = type;
		entity.canonicalName = name;
		entity.aliases = aliases;
		entity.description = description;
		entity.confidence = confidence;
		entity.evidence = List.of(evidence);
		EntityExtractionAiResponse response = new EntityExtractionAiResponse();
		response.entities = List.of(entity);
		return response;
	}
}
