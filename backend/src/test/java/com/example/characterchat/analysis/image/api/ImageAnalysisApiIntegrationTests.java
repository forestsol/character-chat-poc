package com.example.characterchat.analysis.image.api;

import com.example.characterchat.ai.fake.FakeAiClient;
import com.example.characterchat.analysis.entity.application.EntityExtractionAiResponse;
import com.example.characterchat.analysis.image.application.ImageAnalysisAiResponse;
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

@SpringBootTest(properties = "ai.entity-extraction.batch-size=100")
@AutoConfigureMockMvc
class ImageAnalysisApiIntegrationTests {
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
	void 페이지_텍스트_후보와_이미지를_분석해_근거와_사실을_저장한다() throws Exception {
		long bookId = importBook();
		enqueueAliceTextCandidate();
		mockMvc.perform(post("/api/books/{bookId}/entity-candidates/extract", bookId)).andExpect(status().isOk());

		fakeAiClient.enqueueImageStructuredResponse(ImageAnalysisAiResponse.class,
				imageResponse(true, 1, "TEXT_AND_IMAGE", "책 표지에 앨리스가 표현되어 있다"));
		for (int page = 2; page <= 10; page++) {
			fakeAiClient.enqueueImageStructuredResponse(ImageAnalysisAiResponse.class,
					imageResponse(false, 1, "IMAGE", "페이지 " + page + "의 장면"));
		}

		mockMvc.perform(post("/api/books/{bookId}/image-analysis/analyze", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(10))
				.andExpect(jsonPath("$[0].factType").value("ACTION"))
				.andExpect(jsonPath("$[0].sourceType").value("TEXT_AND_IMAGE"))
				.andExpect(jsonPath("$[0].status").value("CONFIRMED"));

		mockMvc.perform(get("/api/books/{bookId}/entity-candidates", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].mentions[1].imageId").isNumber())
				.andExpect(jsonPath("$[0].mentions[1].sourceType").value("TEXT_AND_IMAGE"));

		mockMvc.perform(get("/api/books/{bookId}", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("MULTIMODAL_MERGED"));
	}

	@Test
	void 현재_페이지에_없는_imageOrder는_거부한다() throws Exception {
		long bookId = importBook();
		ImageAnalysisAiResponse invalid = imageResponse(false, 99, "IMAGE", "잘못된 장면");
		fakeAiClient.enqueueImageStructuredResponse(ImageAnalysisAiResponse.class, invalid);

		mockMvc.perform(post("/api/books/{bookId}/image-analysis/analyze", bookId))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("imageOrder")));

		mockMvc.perform(get("/api/books/{bookId}/image-analysis/facts", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 복합_fact_주체가_모두_후보이면_각_후보의_사실로_분리한다() throws Exception {
		long bookId = importBook();
		EntityExtractionAiResponse extraction = new EntityExtractionAiResponse();
		extraction.entities = List.of(
				textCandidate("CHARACTER", "앨리스", 2, "앨리스"),
				textCandidate("CHARACTER", "토끼", 2, "토끼"));
		fakeAiClient.enqueueStructuredResponse(EntityExtractionAiResponse.class, extraction);
		mockMvc.perform(post("/api/books/{bookId}/entity-candidates/extract", bookId)).andExpect(status().isOk());

		ImageAnalysisAiResponse firstPage = imageResponse(false, 1, "IMAGE", "함께 서 있다");
		firstPage.facts.get(0).subjectName = "앨리스와 토끼";
		fakeAiClient.enqueueImageStructuredResponse(ImageAnalysisAiResponse.class, firstPage);
		for (int page = 2; page <= 10; page++) {
			fakeAiClient.enqueueImageStructuredResponse(ImageAnalysisAiResponse.class,
					imageResponse(false, 1, "IMAGE", "페이지 " + page + "의 장면"));
		}

		mockMvc.perform(post("/api/books/{bookId}/image-analysis/analyze", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(11));
	}

	@Test
	void 특정할_수_없는_복합_fact_주체는_미지정_사실로_보존하고_계속한다() throws Exception {
		long bookId = importBook();
		ImageAnalysisAiResponse firstPage = imageResponse(false, 1, "IMAGE", "함께 모여 있다");
		firstPage.facts.get(0).subjectName = "다른 동물과 새들";
		fakeAiClient.enqueueImageStructuredResponse(ImageAnalysisAiResponse.class, firstPage);
		for (int page = 2; page <= 10; page++) {
			fakeAiClient.enqueueImageStructuredResponse(ImageAnalysisAiResponse.class,
					imageResponse(false, 1, "IMAGE", "페이지 " + page + "의 장면"));
		}

		mockMvc.perform(post("/api/books/{bookId}/image-analysis/analyze", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(10))
				.andExpect(jsonPath("$[0].subjectCandidateId").doesNotExist());
	}

	@Test
	void 이름을_특정하지_못한_빈_시각_개체만_제외하고_분석을_계속한다() throws Exception {
		long bookId = importBook();
		ImageAnalysisAiResponse firstPage = imageResponse(false, 1, "IMAGE", "이름을 알 수 없는 인물이 보인다");
		ImageAnalysisAiResponse.VisualEntity unnamed = new ImageAnalysisAiResponse.VisualEntity();
		unnamed.imageOrder = 1; unnamed.entityType = "CHARACTER"; unnamed.observedName = "";
		unnamed.matchedCandidateName = ""; unnamed.description = "이름을 특정할 수 없는 인물"; unnamed.confidence = 0.5;
		firstPage.entities = List.of(unnamed);
		fakeAiClient.enqueueImageStructuredResponse(ImageAnalysisAiResponse.class, firstPage);
		for (int page = 2; page <= 10; page++) {
			fakeAiClient.enqueueImageStructuredResponse(ImageAnalysisAiResponse.class,
					imageResponse(false, 1, "IMAGE", "페이지 " + page + "의 장면"));
		}

		mockMvc.perform(post("/api/books/{bookId}/image-analysis/analyze", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(10));
	}

	private long importBook() throws Exception {
		String json = mockMvc.perform(post("/api/books/import")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"bookDirectory\":\"alice-demo\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(json).get("id").asLong();
	}

	private void enqueueAliceTextCandidate() {
		EntityExtractionAiResponse.MentionEvidence evidence = new EntityExtractionAiResponse.MentionEvidence();
		evidence.sourceOrder = 1;
		evidence.mentionText = "ALICE";
		EntityExtractionAiResponse.ExtractedEntity entity = new EntityExtractionAiResponse.ExtractedEntity();
		entity.entityType = "CHARACTER";
		entity.canonicalName = "Alice";
		entity.aliases = List.of("앨리스");
		entity.description = "이야기의 주인공";
		entity.confidence = 0.98;
		entity.evidence = List.of(evidence);
		EntityExtractionAiResponse response = new EntityExtractionAiResponse();
		response.entities = List.of(entity);
		fakeAiClient.enqueueStructuredResponse(EntityExtractionAiResponse.class, response);
	}

	private EntityExtractionAiResponse.ExtractedEntity textCandidate(String type, String name, int sourceOrder, String mentionText) {
		EntityExtractionAiResponse.MentionEvidence evidence = new EntityExtractionAiResponse.MentionEvidence();
		evidence.sourceOrder = sourceOrder;
		evidence.mentionText = mentionText;
		EntityExtractionAiResponse.ExtractedEntity entity = new EntityExtractionAiResponse.ExtractedEntity();
		entity.entityType = type;
		entity.canonicalName = name;
		entity.aliases = List.of();
		entity.description = "테스트 후보";
		entity.confidence = 0.9;
		entity.evidence = List.of(evidence);
		return entity;
	}

	private ImageAnalysisAiResponse imageResponse(boolean includeAlice, int imageOrder, String sourceType, String value) {
		ImageAnalysisAiResponse response = new ImageAnalysisAiResponse();
		if (includeAlice) {
			ImageAnalysisAiResponse.VisualEntity entity = new ImageAnalysisAiResponse.VisualEntity();
			entity.imageOrder = imageOrder;
			entity.entityType = "CHARACTER";
			entity.observedName = "Alice";
			entity.matchedCandidateName = "Alice";
			entity.description = "표지에 보이는 소녀";
			entity.confidence = 0.9;
			response.entities = List.of(entity);
		} else {
			response.entities = List.of();
		}
		ImageAnalysisAiResponse.VisualFact fact = new ImageAnalysisAiResponse.VisualFact();
		fact.imageOrder = imageOrder;
		fact.factType = "ACTION";
		fact.subjectName = includeAlice ? "Alice" : "";
		fact.value = value;
		fact.sourceType = sourceType;
		fact.status = "CONFIRMED";
		fact.confidence = 0.9;
		fact.description = "테스트 관찰";
		response.facts = List.of(fact);
		return response;
	}
}
