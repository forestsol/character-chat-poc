package com.example.characterchat.analysis.entity.application;

import com.example.characterchat.ai.AiClient;
import com.example.characterchat.ai.AiClientException;
import com.example.characterchat.ai.AiTextRequest;
import com.example.characterchat.analysis.entity.api.EntityCandidateResponse;
import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityMention;
import com.example.characterchat.analysis.entity.domain.EntityType;
import com.example.characterchat.analysis.entity.persistence.EntityCandidateMapper;
import com.example.characterchat.book.domain.Book;
import com.example.characterchat.book.domain.BookParagraph;
import com.example.characterchat.book.persistence.BookMapper;
import com.example.characterchat.common.exception.BookNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EntityExtractionService {
	private static final String SYSTEM_PROMPT = """
			당신은 책 원문에서 개체 후보를 추출하는 분석기입니다.
			CHARACTER, PLACE, OBJECT, ORGANIZATION만 추출하세요.
			관계, 사건, 성격 추론, 대화 가능 여부는 추출하지 마세요.
			각 후보는 정규 이름, 실제 원문에 나온 별칭, 짧은 설명, 0부터 1 사이 신뢰도와 근거를 포함해야 합니다.
			근거의 sourceOrder와 mentionText는 제공된 원문에 실제로 존재하는 것만 사용하세요.
			기존 후보와 같은 개체라면 기존 정규 이름을 그대로 사용하세요.
			""";

	private final AiClient aiClient;
	private final BookMapper bookMapper;
	private final EntityCandidateMapper candidateMapper;
	private final EntityCandidateWriter writer;
	private final EntityExtractionProperties properties;

	public EntityExtractionService(AiClient aiClient, BookMapper bookMapper, EntityCandidateMapper candidateMapper,
			EntityCandidateWriter writer, EntityExtractionProperties properties) {
		this.aiClient = aiClient;
		this.bookMapper = bookMapper;
		this.candidateMapper = candidateMapper;
		this.writer = writer;
		this.properties = properties;
	}

	public List<EntityCandidateResponse> extract(Long bookId) {
		Book book = requireBook(bookId);
		List<BookParagraph> paragraphs = bookMapper.findParagraphsByBookId(bookId);
		if (paragraphs.isEmpty()) {
			throw new EntityExtractionException("분석할 문단이 없습니다.");
		}
		int batchSize = properties.getBatchSize();
		if (batchSize < 1) {
			throw new EntityExtractionException("AI 개체 추출 배치 크기는 1 이상이어야 합니다.");
		}

		CandidateAccumulator accumulator = new CandidateAccumulator();
		try {
			for (int start = 0; start < paragraphs.size(); start += batchSize) {
				List<BookParagraph> batch = paragraphs.subList(start, Math.min(start + batchSize, paragraphs.size()));
				EntityExtractionAiResponse response = aiClient.generateStructured(
						new AiTextRequest(SYSTEM_PROMPT, buildPrompt(book, batch, accumulator.summaries())),
						EntityExtractionAiResponse.class);
				accumulator.add(response, batch);
			}
		} catch (AiClientException exception) {
			throw new EntityExtractionException("AI 개체 후보 추출 호출에 실패했습니다.", exception);
		}

		List<EntityCandidateDraft> drafts = accumulator.toDrafts();
		if (drafts.isEmpty()) throw new EntityExtractionException("검증 가능한 개체 후보가 없습니다.");
		writer.replace(bookId, drafts);
		return getCandidates(bookId);
	}

	@Transactional(readOnly = true)
	public List<EntityCandidateResponse> getCandidates(Long bookId) {
		requireBook(bookId);
		List<EntityCandidate> candidates = candidateMapper.findCandidatesByBookId(bookId);
		List<EntityMention> mentions = candidateMapper.findMentionsByBookId(bookId);
		return EntityCandidateResponse.from(candidates, mentions);
	}

	private Book requireBook(Long bookId) {
		Book book = bookMapper.findBookById(bookId);
		if (book == null) throw new BookNotFoundException(bookId);
		return book;
	}

	private String buildPrompt(Book book, List<BookParagraph> batch, List<String> existing) {
		String candidateText = existing.isEmpty() ? "없음" : String.join("\n", existing);
		String paragraphText = batch.stream()
				.map(p -> "[sourceOrder=" + p.sourceOrder() + "] " + p.content())
				.collect(Collectors.joining("\n\n"));
		return "책 제목: " + book.getTitle() + "\n\n기존 후보:\n" + candidateText
				+ "\n\n분석할 원문:\n" + paragraphText;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
	}

	private static final class CandidateAccumulator {
		private final List<MutableCandidate> candidates = new ArrayList<>();

		void add(EntityExtractionAiResponse response, List<BookParagraph> batch) {
			if (response == null || response.entities == null) {
				throw new EntityExtractionException("AI 개체 추출 응답에 entities가 없습니다.");
			}
			Map<Integer, BookParagraph> paragraphs = batch.stream()
					.collect(Collectors.toMap(BookParagraph::sourceOrder, Function.identity()));
			for (EntityExtractionAiResponse.ExtractedEntity extracted : response.entities) {
				EntityType type = parseType(extracted.entityType);
				String name = requireText(extracted.canonicalName, "canonicalName");
				String description = requireText(extracted.description, "description");
				validateConfidence(extracted.confidence);
				List<String> aliases = extracted.aliases == null ? List.of() : extracted.aliases.stream()
						.filter(alias -> alias != null && !alias.isBlank()).map(String::strip).distinct().toList();
				List<MentionDraft> mentions = validateEvidence(extracted, paragraphs);
				if (mentions.isEmpty()) continue;

				Set<String> identities = new HashSet<>();
				identities.add(normalize(name));
				aliases.stream().map(EntityExtractionService::normalize).forEach(identities::add);
				MutableCandidate target = candidates.stream()
						.filter(candidate -> candidate.type == type && candidate.overlaps(identities))
						.findFirst().orElse(null);
				if (target == null) {
					candidates.add(new MutableCandidate(type, name, description, extracted.confidence, aliases, mentions));
				} else {
					target.merge(name, description, extracted.confidence, aliases, mentions);
				}
			}
		}

		List<String> summaries() {
			return candidates.stream().map(candidate -> "- " + candidate.type + ": " + candidate.canonicalName
					+ " (별칭: " + String.join(", ", candidate.aliases) + ")").toList();
		}

		List<EntityCandidateDraft> toDrafts() { return candidates.stream().map(MutableCandidate::toDraft).toList(); }

		private static EntityType parseType(String value) {
			try { return EntityType.valueOf(requireText(value, "entityType").toUpperCase(Locale.ROOT)); }
			catch (IllegalArgumentException exception) { throw new EntityExtractionException("허용되지 않은 entityType입니다: " + value); }
		}

		private static List<MentionDraft> validateEvidence(EntityExtractionAiResponse.ExtractedEntity entity,
				Map<Integer, BookParagraph> paragraphs) {
			if (entity.evidence == null || entity.evidence.isEmpty()) return List.of();
			List<MentionDraft> result = new ArrayList<>();
			for (EntityExtractionAiResponse.MentionEvidence evidence : entity.evidence) {
				if (evidence == null || evidence.mentionText == null || evidence.mentionText.isBlank()) continue;
				BookParagraph paragraph = paragraphs.get(evidence.sourceOrder);
				if (paragraph == null) continue;
				String mention = evidence.mentionText.strip();
				if (!paragraph.content().contains(mention)) {
					paragraph = paragraphs.values().stream()
							.filter(candidate -> candidate.content().contains(mention))
							.min(java.util.Comparator.comparingInt(candidate -> Math.abs(candidate.sourceOrder() - evidence.sourceOrder)))
							.orElse(null);
				}
				if (paragraph != null) result.add(new MentionDraft(paragraph.id(), mention, entity.confidence));
			}
			return result;
		}

		private static String requireText(String value, String field) {
			if (value == null || value.isBlank()) throw new EntityExtractionException("AI 응답의 " + field + "가 비어 있습니다.");
			return value.strip();
		}

		private static void validateConfidence(double value) {
			if (!Double.isFinite(value) || value < 0 || value > 1) {
				throw new EntityExtractionException("confidence는 0부터 1 사이여야 합니다: " + value);
			}
		}
	}

	private static final class MutableCandidate {
		private final EntityType type;
		private final String canonicalName;
		private String description;
		private double confidence;
		private final Set<String> aliases = new LinkedHashSet<>();
		private final List<MentionDraft> mentions = new ArrayList<>();

		MutableCandidate(EntityType type, String canonicalName, String description, double confidence,
				List<String> aliases, List<MentionDraft> mentions) {
			this.type = type; this.canonicalName = canonicalName; this.description = description; this.confidence = confidence;
			this.aliases.addAll(aliases); this.mentions.addAll(mentions);
		}

		boolean overlaps(Set<String> identities) {
			if (identities.contains(normalize(canonicalName))) return true;
			return aliases.stream().map(EntityExtractionService::normalize).anyMatch(identities::contains);
		}

		void merge(String alternateName, String description, double confidence, List<String> aliases, List<MentionDraft> mentions) {
			if (!normalize(alternateName).equals(normalize(canonicalName))) this.aliases.add(alternateName);
			this.aliases.addAll(aliases);
			if (description.length() > this.description.length()) this.description = description;
			this.confidence = Math.max(this.confidence, confidence);
			Set<String> keys = this.mentions.stream().map(m -> m.paragraphId() + ":" + normalize(m.mentionText())).collect(Collectors.toSet());
			for (MentionDraft mention : mentions) {
				if (keys.add(mention.paragraphId() + ":" + normalize(mention.mentionText()))) this.mentions.add(mention);
			}
		}

		EntityCandidateDraft toDraft() {
			return new EntityCandidateDraft(type, canonicalName, description, confidence, List.copyOf(aliases), List.copyOf(mentions));
		}
	}
}
