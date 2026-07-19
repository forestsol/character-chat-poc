package com.example.characterchat.chat.application;

import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityType;
import com.example.characterchat.analysis.entity.persistence.EntityCandidateMapper;
import com.example.characterchat.analysis.kg.domain.KnowledgeEntity;
import com.example.characterchat.analysis.kg.domain.KnowledgeRelation;
import com.example.characterchat.analysis.kg.persistence.KnowledgeGraphMapper;
import com.example.characterchat.book.application.BookService;
import com.example.characterchat.book.persistence.BookMapper;
import com.example.characterchat.chat.api.ChatResponse;
import com.example.characterchat.profile.domain.CharacterProfile;
import com.example.characterchat.profile.persistence.CharacterProfileMapper;
import com.example.characterchat.rag.application.RagService;
import com.example.characterchat.review.application.CharacterReviewWriter;
import com.example.characterchat.review.domain.CharacterRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_OPENAI_LIVE_TESTS", matches = "true")
@SpringBootTest(properties = "ai.provider=openai")
class ChatLiveIntegrationTests {
	private static final String BOOK_KEY = "alice-demo";
	@Autowired BookService bookService;
	@Autowired BookMapper bookMapper;
	@Autowired EntityCandidateMapper candidateMapper;
	@Autowired KnowledgeGraphMapper graphMapper;
	@Autowired CharacterReviewWriter reviewWriter;
	@Autowired CharacterProfileMapper profileMapper;
	@Autowired RagService ragService;
	@Autowired ChatService chatService;

	@BeforeEach @AfterEach void clean() { bookMapper.deleteBookByBookKey(BOOK_KEY); }

	@Test
	void 실제_OpenAI로_프로필_RAG_KG에_근거한_앨리스_답변을_생성한다() {
		Long bookId = setup();
		ChatResponse response = chatService.chat(bookId, "흰 토끼와 나는 어떤 관계였어?");
		assertThat(response.character().name()).isEqualTo("앨리스");
		assertThat(response.answer()).isNotBlank();
		assertThat(response.grounded()).isTrue();
		assertThat(response.debug().usedParagraphIds().size() + response.debug().usedRelationIds().size()).isPositive();
	}

	private Long setup() {
		Long bookId = bookService.importBook(BOOK_KEY).id();
		EntityCandidate alice = new EntityCandidate(bookId, EntityType.CHARACTER, "앨리스", "이야기의 주인공", 0.98);
		EntityCandidate rabbit = new EntityCandidate(bookId, EntityType.CHARACTER, "흰 토끼", "앨리스가 따라간 토끼", 0.95);
		candidateMapper.insertCandidate(alice); candidateMapper.insertCandidate(rabbit);
		KnowledgeEntity aliceEntity = entity(bookId, alice.getId(), "앨리스");
		KnowledgeEntity rabbitEntity = entity(bookId, rabbit.getId(), "흰 토끼");
		graphMapper.insertKnowledgeEntity(aliceEntity); graphMapper.insertKnowledgeEntity(rabbitEntity);
		graphMapper.insertRelation(new KnowledgeRelation(null, bookId, aliceEntity.getId(), "FOLLOWS",
				rabbitEntity.getId(), "앨리스는 호기심 때문에 흰 토끼를 따라 모험을 시작했다.",
				0.97, "PENDING", null, null));
		reviewWriter.approve(alice, "MAIN", true);
		CharacterRecord character = profileMapper.findChatEnabledCharacterByBookId(bookId);
		profileMapper.insertProfile(profile(character.id()));
		ragService.index(bookId);
		return bookId;
	}

	private KnowledgeEntity entity(Long bookId, Long candidateId, String name) {
		KnowledgeEntity e = new KnowledgeEntity(); e.setBookId(bookId); e.setEntityType("CHARACTER");
		e.setReferenceType("ENTITY_CANDIDATE"); e.setReferenceId(candidateId); e.setName(name);
		e.setDescription(name + " 설명"); e.setReviewStatus("PENDING"); return e;
	}

	private CharacterProfile profile(Long characterId) {
		CharacterProfile p = new CharacterProfile(); p.setCharacterId(characterId); p.setStoryPoint("AFTER_FINAL_EVENT");
		p.setRoleDescription("이야기의 주인공"); p.setAppearance("어린 소녀"); p.setPersonality("호기심이 많고 솔직함");
		p.setValues("직접 확인하고 질문하기"); p.setGoals("겪은 모험을 이해하기"); p.setSpeechStyle("솔직하고 질문을 자주 함");
		p.setMajorExperiences("흰 토끼를 따라 이상한 나라로 들어가 모험함"); p.setAttitudesTowardOthers("낯선 이에게 호기심을 보임");
		p.setKnownFacts("자신이 겪은 이상한 나라의 사건");
		p.setSystemPrompt("앨리스의 1인칭으로 결말 직후 시점에서 답하고 근거 없는 사실은 모른다고 말한다."); return p;
	}
}
