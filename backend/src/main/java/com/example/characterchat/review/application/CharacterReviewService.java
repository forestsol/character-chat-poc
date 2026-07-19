package com.example.characterchat.review.application;

import com.example.characterchat.ai.AiClient;
import com.example.characterchat.ai.AiClientException;
import com.example.characterchat.ai.AiTextRequest;
import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityType;
import com.example.characterchat.analysis.entity.persistence.EntityCandidateMapper;
import com.example.characterchat.book.domain.Book;
import com.example.characterchat.book.persistence.BookMapper;
import com.example.characterchat.common.exception.BookNotFoundException;
import com.example.characterchat.review.api.CharacterReviewRequest;
import com.example.characterchat.review.api.CharacterReviewResponse;
import com.example.characterchat.review.domain.CharacterRecord;
import com.example.characterchat.review.persistence.CharacterReviewMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CharacterReviewService {
	private static final Set<String> ROLES = Set.of("MAIN", "SUPPORTING", "MINOR", "UNKNOWN");
	private static final String SYSTEM_PROMPT = """
			책의 등장인물 후보가 서사에서 차지하는 역할을 추천하세요.
			narrativeRole은 MAIN, SUPPORTING, MINOR, UNKNOWN 중 하나만 사용하세요.
			모든 후보를 정확히 한 번씩 포함하고 추천 이유를 짧게 설명하세요.
			이 추천은 사람 검수 참고용이며 승인, 거절 또는 대화 가능 여부를 결정하지 않습니다.
			""";

	private final AiClient aiClient; private final BookMapper bookMapper; private final EntityCandidateMapper candidateMapper;
	private final CharacterReviewMapper reviewMapper; private final CharacterReviewWriter writer;

	public CharacterReviewService(AiClient aiClient, BookMapper bookMapper, EntityCandidateMapper candidateMapper,
			CharacterReviewMapper reviewMapper, CharacterReviewWriter writer) {
		this.aiClient = aiClient; this.bookMapper = bookMapper; this.candidateMapper = candidateMapper;
		this.reviewMapper = reviewMapper; this.writer = writer;
	}

	public List<CharacterReviewResponse> recommend(Long bookId) {
		Book book = requireBook(bookId);
		List<EntityCandidate> candidates = reviewableCandidates(bookId);
		if (candidates.isEmpty()) throw new CharacterReviewException("추천할 등장인물 후보가 없습니다.");
		String prompt = "책 제목: " + book.getTitle() + "\n\n등장인물 후보:\n" + candidates.stream()
				.map(candidate -> "- " + candidate.getCanonicalName() + ": " + candidate.getDescription())
				.collect(Collectors.joining("\n"));
		try {
			RoleRecommendationAiResponse response = aiClient.generateStructured(
					new AiTextRequest(SYSTEM_PROMPT, prompt), RoleRecommendationAiResponse.class);
			Map<String, EntityCandidate> byName = candidates.stream().collect(Collectors.toMap(
					candidate -> normalize(candidate.getCanonicalName()), candidate -> candidate));
			if (response == null || response.recommendations == null) throw new CharacterReviewException("AI 추천 응답이 비어 있습니다.");
			Map<Long, RecommendationDraft> drafts = new HashMap<>();
			for (RoleRecommendationAiResponse.Recommendation recommendation : response.recommendations) {
				EntityCandidate candidate = byName.get(normalize(recommendation.candidateName));
				if (candidate == null) throw new CharacterReviewException("존재하지 않는 추천 후보입니다: " + recommendation.candidateName);
				String role = role(recommendation.narrativeRole);
				if (recommendation.reason == null || recommendation.reason.isBlank()) throw new CharacterReviewException("추천 이유가 비어 있습니다.");
				if (drafts.put(candidate.getId(), new RecommendationDraft(role, recommendation.reason.strip())) != null)
					throw new CharacterReviewException("중복 추천 후보입니다: " + recommendation.candidateName);
			}
			if (drafts.size() != candidates.size()) throw new CharacterReviewException("모든 등장인물 후보에 대한 추천이 필요합니다.");
			writer.saveRecommendations(drafts);
			return getReviews(bookId);
		} catch (AiClientException exception) {
			throw new RoleRecommendationException("AI 등장인물 역할 추천에 실패했습니다.", exception);
		}
	}

	public List<CharacterReviewResponse> review(Long bookId, Long candidateId, CharacterReviewRequest request) {
		requireBook(bookId);
		EntityCandidate candidate = candidate(bookId, candidateId);
		if (request == null || request.decision() == null) throw new CharacterReviewException("decision은 필수입니다.");
		switch (request.decision().strip().toUpperCase(Locale.ROOT)) {
			case "APPROVE" -> writer.approve(candidate, role(request.narrativeRole()), Boolean.TRUE.equals(request.chatEnabled()));
			case "REJECT" -> writer.reject(candidate);
			case "MERGE" -> {
				if (request.mergeTargetCandidateId() == null) throw new CharacterReviewException("병합 대상 후보 ID가 필요합니다.");
				EntityCandidate target = candidate(bookId, request.mergeTargetCandidateId());
				if (candidate.getId().equals(target.getId())) throw new CharacterReviewException("같은 후보로 병합할 수 없습니다.");
				if (Set.of("REJECTED", "MERGED").contains(target.getReviewStatus())) throw new CharacterReviewException("거절되거나 병합된 후보를 대상으로 선택할 수 없습니다.");
				writer.merge(candidate, target);
			}
			default -> throw new CharacterReviewException("decision은 APPROVE, REJECT, MERGE 중 하나여야 합니다.");
		}
		return getReviews(bookId);
	}

	public List<CharacterReviewResponse> getReviews(Long bookId) {
		requireBook(bookId);
		Map<Long, CharacterRecord> characters = reviewMapper.findCharactersByBookId(bookId).stream()
				.collect(Collectors.toMap(CharacterRecord::candidateId, character -> character));
		return candidateMapper.findCandidatesByBookId(bookId).stream()
				.filter(candidate -> candidate.getEntityType() == EntityType.CHARACTER)
				.map(candidate -> CharacterReviewResponse.from(candidate, characters.get(candidate.getId()))).toList();
	}

	private List<EntityCandidate> reviewableCandidates(Long bookId) {
		return candidateMapper.findCandidatesByBookId(bookId).stream()
				.filter(candidate -> candidate.getEntityType() == EntityType.CHARACTER)
				.filter(candidate -> !Set.of("REJECTED", "MERGED").contains(candidate.getReviewStatus())).toList();
	}
	private EntityCandidate candidate(Long bookId, Long id) {
		return candidateMapper.findCandidatesByBookId(bookId).stream()
				.filter(candidate -> candidate.getId().equals(id) && candidate.getEntityType() == EntityType.CHARACTER)
				.findFirst().orElseThrow(() -> new CharacterReviewException("등장인물 후보를 찾을 수 없습니다: " + id));
	}
	private Book requireBook(Long id) { Book book = bookMapper.findBookById(id); if (book == null) throw new BookNotFoundException(id); return book; }
	private String role(String value) { String role = value == null ? "" : value.strip().toUpperCase(Locale.ROOT); if (!ROLES.contains(role)) throw new CharacterReviewException("narrativeRole이 올바르지 않습니다: " + value); return role; }
	private static String normalize(String value) { return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", ""); }
}
