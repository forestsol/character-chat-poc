package com.example.characterchat.review.api;

import com.example.characterchat.ai.fake.FakeAiClient;
import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityMention;
import com.example.characterchat.analysis.entity.domain.EntityType;
import com.example.characterchat.analysis.entity.persistence.EntityCandidateMapper;
import com.example.characterchat.analysis.kg.domain.KnowledgeEntity;
import com.example.characterchat.analysis.kg.persistence.KnowledgeGraphMapper;
import com.example.characterchat.book.application.BookService;
import com.example.characterchat.book.domain.BookParagraph;
import com.example.characterchat.book.persistence.BookMapper;
import com.example.characterchat.review.application.RoleRecommendationAiResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CharacterReviewApiIntegrationTests {
	private static final String BOOK_KEY = "alice-demo";
	@Autowired MockMvc mockMvc; @Autowired BookService bookService; @Autowired BookMapper bookMapper;
	@Autowired EntityCandidateMapper candidateMapper; @Autowired KnowledgeGraphMapper graphMapper;
	@Autowired FakeAiClient fakeAiClient;

	@BeforeEach @AfterEach void clean() { fakeAiClient.clear(); bookMapper.deleteBookByBookKey(BOOK_KEY); }

	@Test
	void AI_추천을_저장하고_사람이_주인공을_대화_인물로_승인한다() throws Exception {
		Setup setup = setupCharacters();
		RoleRecommendationAiResponse response = new RoleRecommendationAiResponse();
		response.recommendations = List.of(recommendation("앨리스", "MAIN"), recommendation("토끼", "SUPPORTING"));
		fakeAiClient.enqueueStructuredResponse(RoleRecommendationAiResponse.class, response);

		mockMvc.perform(post("/api/books/{bookId}/character-reviews/recommend", setup.bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.candidateId == " + setup.alice.getId() + ")].recommendedRole")
						.value(org.hamcrest.Matchers.hasItem("MAIN")));

		mockMvc.perform(put("/api/books/{bookId}/character-reviews/{candidateId}", setup.bookId, setup.alice.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"APPROVE\",\"narrativeRole\":\"MAIN\",\"chatEnabled\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.candidateId == " + setup.alice.getId() + ")].reviewStatus")
						.value(org.hamcrest.Matchers.hasItem("APPROVED")))
				.andExpect(jsonPath("$[?(@.candidateId == " + setup.alice.getId() + ")].narrativeRole")
						.value(org.hamcrest.Matchers.hasItem("MAIN")))
				.andExpect(jsonPath("$[?(@.candidateId == " + setup.alice.getId() + ")].chatEnabled")
						.value(org.hamcrest.Matchers.hasItem(true)));

		mockMvc.perform(get("/api/books/{bookId}", setup.bookId))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHARACTERS_REVIEWED"));
	}

	@Test
	void 대화_인물은_책마다_한_명만_허용한다() throws Exception {
		Setup setup = setupCharacters();
		approve(setup.bookId, setup.alice.getId(), true).andExpect(status().isOk());
		approve(setup.bookId, setup.rabbit.getId(), true)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("한 명")));
	}

	@Test
	void 동일_인물_후보를_병합하면_source는_MERGED로_남는다() throws Exception {
		Setup setup = setupCharacters();
		BookParagraph paragraph = bookMapper.findParagraphsByBookId(setup.bookId).get(0);
		candidateMapper.insertMention(new EntityMention(null, setup.rabbit.getId(), paragraph.id(), null,
				"흰 토끼", "TEXT", 0.9));

		mockMvc.perform(put("/api/books/{bookId}/character-reviews/{candidateId}", setup.bookId, setup.rabbit.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"MERGE\",\"mergeTargetCandidateId\":" + setup.alice.getId() + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.candidateId == " + setup.rabbit.getId() + ")].reviewStatus")
						.value(org.hamcrest.Matchers.hasItem("MERGED")))
				.andExpect(jsonPath("$[?(@.candidateId == " + setup.rabbit.getId() + ")].mergedIntoCandidateId")
						.value(org.hamcrest.Matchers.hasItem(setup.alice.getId().intValue())));
	}

	private org.springframework.test.web.servlet.ResultActions approve(Long bookId, Long candidateId, boolean enabled) throws Exception {
		return mockMvc.perform(put("/api/books/{bookId}/character-reviews/{candidateId}", bookId, candidateId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"decision\":\"APPROVE\",\"narrativeRole\":\"SUPPORTING\",\"chatEnabled\":" + enabled + "}"));
	}

	private Setup setupCharacters() {
		Long bookId = bookService.importBook(BOOK_KEY).id();
		EntityCandidate alice = candidate(bookId, "앨리스"); EntityCandidate rabbit = candidate(bookId, "토끼");
		candidateMapper.insertCandidate(alice); candidateMapper.insertCandidate(rabbit);
		graphMapper.insertKnowledgeEntity(knowledge(bookId, alice)); graphMapper.insertKnowledgeEntity(knowledge(bookId, rabbit));
		return new Setup(bookId, alice, rabbit);
	}

	private EntityCandidate candidate(Long bookId, String name) { return new EntityCandidate(bookId, EntityType.CHARACTER, name, name + " 후보", 0.95); }
	private KnowledgeEntity knowledge(Long bookId, EntityCandidate candidate) {
		KnowledgeEntity entity = new KnowledgeEntity(); entity.setBookId(bookId); entity.setEntityType("CHARACTER");
		entity.setReferenceType("ENTITY_CANDIDATE"); entity.setReferenceId(candidate.getId()); entity.setName(candidate.getCanonicalName());
		entity.setDescription(candidate.getDescription()); entity.setReviewStatus("PENDING"); return entity;
	}
	private RoleRecommendationAiResponse.Recommendation recommendation(String name, String role) {
		RoleRecommendationAiResponse.Recommendation value = new RoleRecommendationAiResponse.Recommendation();
		value.candidateName = name; value.narrativeRole = role; value.reason = name + "의 서사 비중"; return value;
	}
	private record Setup(Long bookId, EntityCandidate alice, EntityCandidate rabbit) {}
}
