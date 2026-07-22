package com.example.characterchat.analysis.kg.api;

import com.example.characterchat.ai.fake.FakeAiClient;
import com.example.characterchat.analysis.entity.application.EntityExtractionAiResponse;
import com.example.characterchat.analysis.kg.application.KgExtractionAiResponse;
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
class KnowledgeGraphApiIntegrationTests {
	private static final String BOOK_KEY = "alice-demo";
	@Autowired MockMvc mockMvc; @Autowired BookMapper bookMapper; @Autowired FakeAiClient fakeAiClient; @Autowired ObjectMapper objectMapper;

	@BeforeEach @AfterEach void clean() { fakeAiClient.clear(); bookMapper.deleteBookByBookKey(BOOK_KEY); }

	@Test
	void 사건_참여자와_직접_관계를_PostgreSQL_KG로_구축한다() throws Exception {
		long bookId = importAndExtractCandidates();
		fakeAiClient.enqueueStructuredResponse(KgExtractionAiResponse.class, kgResponse(2));

		mockMvc.perform(post("/api/books/{bookId}/kg/build", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.events.length()").value(1))
				.andExpect(jsonPath("$.events[0].participants.length()").value(2))
				.andExpect(jsonPath("$.entities.length()").value(3))
				.andExpect(jsonPath("$.entities[2].reviewStatus").value("PENDING"))
				.andExpect(jsonPath("$.relations[0].relationType").value("FOLLOWED"))
				.andExpect(jsonPath("$.relations[0].evidenceParagraphId").isNumber());

		mockMvc.perform(get("/api/books/{bookId}/kg", bookId))
				.andExpect(status().isOk()).andExpect(jsonPath("$.relations.length()").value(1));
		mockMvc.perform(get("/api/books/{bookId}", bookId))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("KG_BUILT"));
	}

	@Test
	void 존재하지_않는_근거는_거부하고_KG를_저장하지_않는다() throws Exception {
		long bookId = importAndExtractCandidates();
		fakeAiClient.enqueueStructuredResponse(KgExtractionAiResponse.class, kgResponse(999));
		mockMvc.perform(post("/api/books/{bookId}/kg/build", bookId))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("sourceOrder")));
		mockMvc.perform(get("/api/books/{bookId}/kg", bookId))
				.andExpect(status().isOk()).andExpect(jsonPath("$.entities.length()").value(0));
	}

	@Test
	void 후보에_없는_관계_주체나_대상만_제외하고_사건은_저장한다() throws Exception {
		long bookId = importAndExtractCandidates();
		KgExtractionAiResponse response = kgResponse(2);
		response.relations.get(0).targetCandidateName = "다른 동물과 새들";
		fakeAiClient.enqueueStructuredResponse(KgExtractionAiResponse.class, response);

		mockMvc.perform(post("/api/books/{bookId}/kg/build", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.events.length()").value(1))
				.andExpect(jsonPath("$.events[0].participants.length()").value(2))
				.andExpect(jsonPath("$.relations.length()").value(0));
	}

	@Test
	void 페이지에_이미지가_하나면_잘못된_imageOrder를_유일한_이미지로_교정한다() throws Exception {
		long bookId = importAndExtractCandidates();
		KgExtractionAiResponse response = kgResponse(2);
		KgExtractionAiResponse.Evidence imageEvidence = response.events.get(0).evidence;
		imageEvidence.sourceOrder = 0;
		imageEvidence.pageNumber = 2;
		imageEvidence.imageOrder = 2;
		fakeAiClient.enqueueStructuredResponse(KgExtractionAiResponse.class, response);

		mockMvc.perform(post("/api/books/{bookId}/kg/build", bookId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.events[0].evidenceParagraphId").doesNotExist())
				.andExpect(jsonPath("$.events[0].evidenceImageId").isNumber())
				.andExpect(jsonPath("$.relations[0].evidenceImageId").isNumber());
	}

	private long importAndExtractCandidates() throws Exception {
		String json = mockMvc.perform(post("/api/books/import").contentType(MediaType.APPLICATION_JSON)
				.content("{\"bookDirectory\":\"alice-demo\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long bookId = objectMapper.readTree(json).get("id").asLong();
		EntityExtractionAiResponse response = new EntityExtractionAiResponse();
		response.entities = List.of(entity("앨리스", "ALICE", 1), entity("토끼", "토끼", 2));
		fakeAiClient.enqueueStructuredResponse(EntityExtractionAiResponse.class, response);
		mockMvc.perform(post("/api/books/{bookId}/entity-candidates/extract", bookId)).andExpect(status().isOk());
		return bookId;
	}

	private EntityExtractionAiResponse.ExtractedEntity entity(String name, String mention, int sourceOrder) {
		EntityExtractionAiResponse.MentionEvidence evidence = new EntityExtractionAiResponse.MentionEvidence();
		evidence.sourceOrder = sourceOrder; evidence.mentionText = mention;
		EntityExtractionAiResponse.ExtractedEntity entity = new EntityExtractionAiResponse.ExtractedEntity();
		entity.entityType = "CHARACTER"; entity.canonicalName = name; entity.aliases = List.of();
		entity.description = name + " 후보"; entity.confidence = 0.95; entity.evidence = List.of(evidence);
		return entity;
	}

	private KgExtractionAiResponse kgResponse(int sourceOrder) {
		KgExtractionAiResponse.Evidence evidence = new KgExtractionAiResponse.Evidence();
		evidence.sourceOrder = sourceOrder; evidence.pageNumber = 0; evidence.imageOrder = 0;
		KgExtractionAiResponse.Participant alice = participant("앨리스", "FOLLOWER", evidence);
		KgExtractionAiResponse.Participant rabbit = participant("토끼", "FOLLOWED", evidence);
		KgExtractionAiResponse.Event event = new KgExtractionAiResponse.Event();
		event.name = "앨리스가 토끼를 따라감"; event.description = "앨리스가 토끼를 발견하고 따라간다.";
		event.sequenceOrder = 1; event.confidence = 0.96; event.evidence = evidence; event.participants = List.of(alice, rabbit);
		KgExtractionAiResponse.Relation relation = new KgExtractionAiResponse.Relation();
		relation.sourceCandidateName = "앨리스"; relation.relationType = "FOLLOWED"; relation.targetCandidateName = "토끼";
		relation.description = "앨리스가 토끼를 따라갔다."; relation.confidence = 0.96; relation.evidence = evidence;
		KgExtractionAiResponse response = new KgExtractionAiResponse(); response.events = List.of(event); response.relations = List.of(relation);
		return response;
	}

	private KgExtractionAiResponse.Participant participant(String name, String role, KgExtractionAiResponse.Evidence evidence) {
		KgExtractionAiResponse.Participant participant = new KgExtractionAiResponse.Participant();
		participant.candidateName = name; participant.role = role; participant.evidence = evidence; return participant;
	}
}
