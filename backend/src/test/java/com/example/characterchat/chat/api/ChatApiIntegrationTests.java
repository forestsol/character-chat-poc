package com.example.characterchat.chat.api;

import com.example.characterchat.ai.fake.FakeAiClient;
import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityType;
import com.example.characterchat.analysis.entity.persistence.EntityCandidateMapper;
import com.example.characterchat.analysis.kg.domain.KnowledgeEntity;
import com.example.characterchat.analysis.kg.domain.KnowledgeRelation;
import com.example.characterchat.analysis.kg.persistence.KnowledgeGraphMapper;
import com.example.characterchat.book.application.BookService;
import com.example.characterchat.book.persistence.BookMapper;
import com.example.characterchat.chat.application.ChatAiResponse;
import com.example.characterchat.chat.application.ChatRewriteAiResponse;
import com.example.characterchat.profile.domain.CharacterProfile;
import com.example.characterchat.profile.persistence.CharacterProfileMapper;
import com.example.characterchat.rag.application.RagService;
import com.example.characterchat.rag.domain.RagParagraph;
import com.example.characterchat.rag.persistence.RagMapper;
import com.example.characterchat.review.application.CharacterReviewWriter;
import com.example.characterchat.review.domain.CharacterRecord;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatApiIntegrationTests {
	private static final String BOOK_KEY = "alice-demo";
	@Autowired MockMvc mockMvc;
	@Autowired ObjectMapper objectMapper;
	@Autowired BookService bookService;
	@Autowired BookMapper bookMapper;
	@Autowired EntityCandidateMapper candidateMapper;
	@Autowired KnowledgeGraphMapper graphMapper;
	@Autowired CharacterReviewWriter reviewWriter;
	@Autowired CharacterProfileMapper profileMapper;
	@Autowired RagMapper ragMapper;
	@Autowired RagService ragService;
	@Autowired FakeAiClient fakeAiClient;

	@BeforeEach @AfterEach void clean() { fakeAiClient.clear(); bookMapper.deleteBookByBookKey(BOOK_KEY); }

	@Test
	void 프로필_RAG_KG를_조합해_근거가_있는_캐릭터_답변을_생성한다() throws Exception {
		Setup setup = setup();
		ChatAiResponse ai = response(true, "난 호기심이 나서 흰 토끼를 따라갔어.",
				List.of(setup.paragraph.id()), List.of(setup.relation.id()));
		fakeAiClient.enqueueStructuredResponse(ChatAiResponse.class, ai);

		mockMvc.perform(post("/api/books/{bookId}/chat", setup.bookId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new ChatRequest(setup.paragraph.content()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.character.name").value("앨리스"))
				.andExpect(jsonPath("$.character.storyPoint").value("AFTER_FINAL_EVENT"))
				.andExpect(jsonPath("$.answer").value("난 호기심이 나서 흰 토끼를 따라갔어."))
				.andExpect(jsonPath("$.grounded").value(true))
				.andExpect(jsonPath("$.debug.usedParagraphIds[0]").value(setup.paragraph.id()))
				.andExpect(jsonPath("$.debug.usedRelationIds[0]").value(setup.relation.id()))
				.andExpect(jsonPath("$.debug.ragRanges").isNotEmpty())
				.andExpect(jsonPath("$.debug.directRelations[0].targetName").value("흰 토끼"));
		assertThat(fakeAiClient.getLastTextRequest().userPrompt())
				.contains("캐릭터: 앨리스", "관련 원문", "흰 토끼", "relationId=" + setup.relation.id());
		assertThat(fakeAiClient.getLastTextRequest().systemPrompt())
				.contains("관련 행동, 대사, 사건이 있으면 supported=true", "분석 시스템 표현을 사용하지 마세요");
		mockMvc.perform(get("/api/books/{bookId}", setup.bookId))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CHAT_READY"));
	}

	@Test
	void 근거가_부족하면_AI가_쓴_내용_대신_모른다는_답변을_반환한다() throws Exception {
		Setup setup = setup();
		fakeAiClient.enqueueStructuredResponse(ChatAiResponse.class,
				response(false, "근거 없이 만든 답", List.of(), List.of()));

		mockMvc.perform(post("/api/books/{bookId}/chat", setup.bookId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new ChatRequest("내가 달에 간 적이 있어?"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.grounded").value(false))
				.andExpect(jsonPath("$.answer").value("글쎄, 그건 나도 잘 모르겠어."))
				.andExpect(jsonPath("$.debug.usedParagraphIds").isEmpty())
				.andExpect(jsonPath("$.debug.usedRelationIds").isEmpty());
	}

	@Test
	void 제공하지_않은_근거_ID를_인용한_답변은_거부한다() throws Exception {
		Setup setup = setup();
		fakeAiClient.enqueueStructuredResponse(ChatAiResponse.class,
				response(true, "잘못된 근거의 답", List.of(999999L), List.of()));
		mockMvc.perform(post("/api/books/{bookId}/chat", setup.bookId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new ChatRequest(setup.paragraph.content()))))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("제공되지 않은 원문")));
	}

	@Test
	void rewrites_contextual_question_before_search() throws Exception {
		Setup setup = setup();
		fakeAiClient.enqueueStructuredResponse(ChatRewriteAiResponse.class,
				rewrite(true, "흰 토끼는 왜 뛰어가고 있었어?", "", List.of()));
		fakeAiClient.enqueueStructuredResponse(ChatAiResponse.class,
				response(true, "그 토끼는 아주 바빠 보였어.", List.of(), List.of(setup.relation.id())));

		ChatRequest request = new ChatRequest("그때 걔는 왜 뛰었어?", List.of(
				new ChatRequest.HistoryMessage("USER", "흰 토끼를 처음 봤을 때 어땠어?"),
				new ChatRequest.HistoryMessage("ASSISTANT", "아주 바빠 보였어.")));
		mockMvc.perform(post("/api/books/{bookId}/chat", setup.bookId)
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.responseType").value("ANSWER"))
				.andExpect(jsonPath("$.debug.rewrite.attempted").value(true))
				.andExpect(jsonPath("$.debug.rewrite.resolved").value(true))
				.andExpect(jsonPath("$.debug.rewrite.fallback").value(false))
				.andExpect(jsonPath("$.debug.rewrite.standaloneQuery").value("흰 토끼는 왜 뛰어가고 있었어?"));
		assertThat(fakeAiClient.getLastTextRequest().userPrompt())
				.contains("사용자의 현재 질문: 그때 걔는 왜 뛰었어?", "검색용으로 해석한 독립 질문: 흰 토끼는 왜 뛰어가고 있었어?");
	}

	@Test
	void asks_for_clarification_when_reference_is_ambiguous() throws Exception {
		Setup setup = setup();
		fakeAiClient.enqueueStructuredResponse(ChatRewriteAiResponse.class,
				rewrite(false, "", "그 사람", List.of("하트 여왕", "흰 토끼")));
		fakeAiClient.enqueueStructuredResponse(ChatAiResponse.class,
				response(false, "하트 여왕과 흰 토끼 중 누구를 말하는 거야?", List.of(), List.of()));

		ChatRequest request = new ChatRequest("그 사람은 왜 그랬어?", List.of(
				new ChatRequest.HistoryMessage("USER", "하트 여왕과 흰 토끼를 만났지?"),
				new ChatRequest.HistoryMessage("ASSISTANT", "응, 둘 다 만났어.")));
		mockMvc.perform(post("/api/books/{bookId}/chat", setup.bookId)
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.responseType").value("CLARIFICATION"))
				.andExpect(jsonPath("$.grounded").value(false))
				.andExpect(jsonPath("$.answer").value("하트 여왕과 흰 토끼 중 누구를 말하는 거야?"))
				.andExpect(jsonPath("$.debug.ragRanges").isEmpty())
				.andExpect(jsonPath("$.debug.rewrite.referentCandidates[0]").value("하트 여왕"));
	}

	@Test
	void falls_back_to_original_question_when_rewrite_fails() throws Exception {
		Setup setup = setup();
		fakeAiClient.enqueueStructuredFailure(ChatRewriteAiResponse.class, "재작성 일시 실패");
		fakeAiClient.enqueueStructuredResponse(ChatAiResponse.class,
				response(true, "원래 질문으로 찾아봤어.", List.of(setup.paragraph.id()), List.of()));

		ChatRequest request = new ChatRequest(setup.paragraph.content(), List.of(
				new ChatRequest.HistoryMessage("USER", "앞에서 무슨 일이 있었지?"),
				new ChatRequest.HistoryMessage("ASSISTANT", "이런 일이 있었어.")));
		mockMvc.perform(post("/api/books/{bookId}/chat", setup.bookId)
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.debug.rewrite.fallback").value(true))
				.andExpect(jsonPath("$.debug.rewrite.standaloneQuery").value(setup.paragraph.content()));
	}

	private Setup setup() {
		Long bookId = bookService.importBook(BOOK_KEY).id();
		EntityCandidate alice = new EntityCandidate(bookId, EntityType.CHARACTER, "앨리스", "이야기의 주인공", 0.98);
		EntityCandidate rabbit = new EntityCandidate(bookId, EntityType.CHARACTER, "흰 토끼", "앨리스가 따라간 토끼", 0.95);
		candidateMapper.insertCandidate(alice);
		candidateMapper.insertCandidate(rabbit);
		KnowledgeEntity aliceEntity = entity(bookId, "CHARACTER", "ENTITY_CANDIDATE", alice.getId(), "앨리스");
		KnowledgeEntity rabbitEntity = entity(bookId, "CHARACTER", "ENTITY_CANDIDATE", rabbit.getId(), "흰 토끼");
		graphMapper.insertKnowledgeEntity(aliceEntity);
		graphMapper.insertKnowledgeEntity(rabbitEntity);
		KnowledgeRelation relationDraft = new KnowledgeRelation(null, bookId, aliceEntity.getId(), "FOLLOWS",
				rabbitEntity.getId(), "앨리스가 흰 토끼를 따라간다", 0.97, "PENDING", null, null);
		graphMapper.insertRelation(relationDraft);
		KnowledgeRelation relation = graphMapper.findRelationsByBookId(bookId).get(0);
		reviewWriter.approve(alice, "MAIN", true);
		CharacterRecord character = profileMapper.findChatEnabledCharacterByBookId(bookId);
		profileMapper.insertProfile(profile(character.id()));
		ragService.index(bookId);
		RagParagraph paragraph = ragMapper.findParagraphsByBookId(bookId).get(0);
		return new Setup(bookId, paragraph, relation);
	}

	private KnowledgeEntity entity(Long bookId, String type, String referenceType, Long referenceId, String name) {
		KnowledgeEntity entity = new KnowledgeEntity(); entity.setBookId(bookId); entity.setEntityType(type);
		entity.setReferenceType(referenceType); entity.setReferenceId(referenceId); entity.setName(name);
		entity.setDescription(name + " 설명"); entity.setReviewStatus("PENDING"); return entity;
	}

	private CharacterProfile profile(Long characterId) {
		CharacterProfile p = new CharacterProfile(); p.setCharacterId(characterId); p.setStoryPoint("AFTER_FINAL_EVENT");
		p.setRoleDescription("이야기의 주인공"); p.setAppearance("소녀"); p.setPersonality("호기심이 많음");
		p.setValues("탐구"); p.setGoals("이상한 세계 이해"); p.setSpeechStyle("질문을 자주 함");
		p.setMajorExperiences("흰 토끼를 따라 모험함"); p.setAttitudesTowardOthers("호기심을 보임");
		p.setKnownFacts("이상한 세계에서 겪은 사건");
		p.setSystemPrompt("앨리스의 1인칭으로 결말 직후 시점에서 답한다."); return p;
	}

	private ChatAiResponse response(boolean supported, String answer, List<Long> paragraphIds, List<Long> relationIds) {
		ChatAiResponse response = new ChatAiResponse(); response.supported = supported; response.answer = answer;
		response.usedParagraphIds = paragraphIds; response.usedRelationIds = relationIds; return response;
	}

	private ChatRewriteAiResponse rewrite(boolean resolved, String standaloneQuery, String ambiguousReference,
	                                      List<String> candidates) {
		ChatRewriteAiResponse response = new ChatRewriteAiResponse();
		response.resolved = resolved;
		response.standaloneQuery = standaloneQuery;
		response.ambiguousReference = ambiguousReference;
		response.referentCandidates = candidates;
		return response;
	}

	private record Setup(Long bookId, RagParagraph paragraph, KnowledgeRelation relation) { }
}
