package com.example.characterchat.profile.application;

import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityType;
import com.example.characterchat.analysis.entity.persistence.EntityCandidateMapper;
import com.example.characterchat.analysis.kg.domain.KnowledgeEntity;
import com.example.characterchat.analysis.kg.persistence.KnowledgeGraphMapper;
import com.example.characterchat.book.application.BookService;
import com.example.characterchat.book.persistence.BookMapper;
import com.example.characterchat.profile.api.CharacterProfileResponse;
import com.example.characterchat.review.application.CharacterReviewWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named="RUN_OPENAI_LIVE_TESTS",matches="true")
@SpringBootTest(properties="ai.provider=openai")
class CharacterProfileLiveIntegrationTests {
	private static final String BOOK_KEY="alice-demo";
	@Autowired BookService bookService; @Autowired BookMapper bookMapper; @Autowired EntityCandidateMapper candidateMapper;
	@Autowired KnowledgeGraphMapper graphMapper; @Autowired CharacterReviewWriter reviewWriter;
	@Autowired CharacterProfileService profileService;

	@BeforeEach @AfterEach void clean(){bookMapper.deleteBookByBookKey(BOOK_KEY);}

	@Test
	void 실제_원문에서_결말_직후_앨리스_프로필과_근거를_생성한다(){
		Long bookId=bookService.importBook(BOOK_KEY).id();
		EntityCandidate candidate=new EntityCandidate(bookId,EntityType.CHARACTER,"앨리스","이야기의 주인공",0.98);
		candidateMapper.insertCandidate(candidate);
		KnowledgeEntity entity=new KnowledgeEntity(); entity.setBookId(bookId); entity.setEntityType("CHARACTER");
		entity.setReferenceType("ENTITY_CANDIDATE"); entity.setReferenceId(candidate.getId()); entity.setName("앨리스");
		entity.setDescription(candidate.getDescription()); entity.setReviewStatus("PENDING"); graphMapper.insertKnowledgeEntity(entity);
		reviewWriter.approve(candidate,"MAIN",true);

		CharacterProfileResponse profile=profileService.generate(bookId);

		assertThat(profile.storyPoint()).isEqualTo("AFTER_FINAL_EVENT");
		assertThat(profile.systemPrompt()).isNotBlank();
		assertThat(profile.evidence()).isNotEmpty();
		assertThat(profile.evidence()).allMatch(e -> e.paragraphId()!=null || e.imageId()!=null);
	}
}
