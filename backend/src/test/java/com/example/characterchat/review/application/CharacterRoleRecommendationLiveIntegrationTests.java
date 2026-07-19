package com.example.characterchat.review.application;

import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityType;
import com.example.characterchat.analysis.entity.persistence.EntityCandidateMapper;
import com.example.characterchat.book.application.BookService;
import com.example.characterchat.book.persistence.BookMapper;
import com.example.characterchat.review.api.CharacterReviewResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_OPENAI_LIVE_TESTS", matches = "true")
@SpringBootTest(properties = "ai.provider=openai")
class CharacterRoleRecommendationLiveIntegrationTests {
	private static final String BOOK_KEY = "alice-demo";
	@Autowired BookService bookService; @Autowired BookMapper bookMapper;
	@Autowired EntityCandidateMapper candidateMapper; @Autowired CharacterReviewService reviewService;

	@BeforeEach @AfterEach void clean() { bookMapper.deleteBookByBookKey(BOOK_KEY); }

	@Test
	void 실제_모델이_등장인물별_서사_역할을_추천한다() {
		Long bookId = bookService.importBook(BOOK_KEY).id();
		insert(bookId, "앨리스", "이야기 전반을 경험하는 인물");
		insert(bookId, "토끼", "앨리스가 따라가는 인물");
		insert(bookId, "모자 장수", "다과회 장면의 인물");

		List<CharacterReviewResponse> reviews = reviewService.recommend(bookId);

		assertThat(reviews).hasSize(3);
		assertThat(reviews).allMatch(review -> Set.of("MAIN", "SUPPORTING", "MINOR", "UNKNOWN").contains(review.recommendedRole()));
		assertThat(reviews).allMatch(review -> review.recommendationReason() != null && !review.recommendationReason().isBlank());
		assertThat(reviews).allMatch(review -> review.reviewStatus().equals("PENDING"));
	}

	private void insert(Long bookId, String name, String description) {
		candidateMapper.insertCandidate(new EntityCandidate(bookId, EntityType.CHARACTER, name, description, 0.95));
	}
}
