package com.example.characterchat.profile.api;

import com.example.characterchat.ai.fake.FakeAiClient;
import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityType;
import com.example.characterchat.analysis.entity.persistence.EntityCandidateMapper;
import com.example.characterchat.analysis.kg.domain.KnowledgeEntity;
import com.example.characterchat.analysis.kg.persistence.KnowledgeGraphMapper;
import com.example.characterchat.book.application.BookService;
import com.example.characterchat.book.persistence.BookMapper;
import com.example.characterchat.profile.application.ProfileGenerationAiResponse;
import com.example.characterchat.review.application.CharacterReviewWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CharacterProfileApiIntegrationTests {
	private static final String BOOK_KEY="alice-demo";
	@Autowired MockMvc mockMvc; @Autowired BookService bookService; @Autowired BookMapper bookMapper;
	@Autowired EntityCandidateMapper candidateMapper; @Autowired KnowledgeGraphMapper graphMapper;
	@Autowired CharacterReviewWriter reviewWriter; @Autowired FakeAiClient fakeAiClient;

	@BeforeEach @AfterEach void clean(){ fakeAiClient.clear(); bookMapper.deleteBookByBookKey(BOOK_KEY); }

	@Test
	void 활성_캐릭터의_결말_직후_프로필과_근거를_생성한다() throws Exception {
		Setup setup=setupActiveCharacter(); fakeAiClient.enqueueStructuredResponse(ProfileGenerationAiResponse.class,response(1));
		mockMvc.perform(post("/api/books/{bookId}/character-profile/generate",setup.bookId))
				.andExpect(status().isOk()).andExpect(jsonPath("$.storyPoint").value("AFTER_FINAL_EVENT"))
				.andExpect(jsonPath("$.roleDescription").value("이야기의 주인공"))
				.andExpect(jsonPath("$.evidence[0].inferenceType").value("EXPLICIT"))
				.andExpect(jsonPath("$.evidence[0].paragraphId").isNumber());
		mockMvc.perform(get("/api/books/{bookId}/character-profile",setup.bookId))
				.andExpect(status().isOk()).andExpect(jsonPath("$.systemPrompt").isNotEmpty());
		mockMvc.perform(get("/api/books/{bookId}",setup.bookId))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROFILE_GENERATED"));
	}

	@Test
	void 존재하지_않는_근거는_거부하고_기존_프로필을_만들지_않는다() throws Exception {
		Setup setup=setupActiveCharacter(); fakeAiClient.enqueueStructuredResponse(ProfileGenerationAiResponse.class,response(999));
		mockMvc.perform(post("/api/books/{bookId}/character-profile/generate",setup.bookId))
				.andExpect(status().isBadGateway()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("sourceOrder")));
		mockMvc.perform(get("/api/books/{bookId}/character-profile",setup.bookId)).andExpect(status().isNotFound());
	}

	private Setup setupActiveCharacter(){
		Long bookId=bookService.importBook(BOOK_KEY).id();
		EntityCandidate candidate=new EntityCandidate(bookId,EntityType.CHARACTER,"앨리스","이야기의 주인공",0.98);
		candidateMapper.insertCandidate(candidate);
		KnowledgeEntity entity=new KnowledgeEntity(); entity.setBookId(bookId); entity.setEntityType("CHARACTER");
		entity.setReferenceType("ENTITY_CANDIDATE"); entity.setReferenceId(candidate.getId()); entity.setName("앨리스");
		entity.setDescription(candidate.getDescription()); entity.setReviewStatus("PENDING"); graphMapper.insertKnowledgeEntity(entity);
		reviewWriter.approve(candidate,"MAIN",true); return new Setup(bookId,candidate);
	}

	private ProfileGenerationAiResponse response(int sourceOrder){
		ProfileGenerationAiResponse r=new ProfileGenerationAiResponse();
		r.roleDescription="이야기의 주인공"; r.appearance="소녀"; r.personality="호기심이 많음"; r.values="탐구";
		r.goals="이상한 세계를 이해하고 돌아가기"; r.speechStyle="질문을 자주 함"; r.majorExperiences="토끼를 따라 모험함";
		r.attitudesTowardOthers="낯선 이들에게 호기심을 보임"; r.knownFacts="이상한 세계의 사건을 경험함";
		r.systemPrompt="앨리스의 1인칭으로 결말 직후 시점에서 답하고 모르는 사실은 모른다고 말한다.";
		ProfileGenerationAiResponse.Evidence e=new ProfileGenerationAiResponse.Evidence();
		e.profileField="ROLE_DESCRIPTION"; e.sourceOrder=sourceOrder; e.pageNumber=0; e.imageOrder=0;
		e.sourceType="TEXT"; e.inferenceType="EXPLICIT"; e.description="앨리스가 이야기의 중심에 있음"; e.confidence=0.98;
		r.evidence= List.of(e); return r;
	}
	private record Setup(Long bookId,EntityCandidate candidate){}
}
