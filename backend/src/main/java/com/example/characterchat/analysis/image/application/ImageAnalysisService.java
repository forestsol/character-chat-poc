package com.example.characterchat.analysis.image.application;

import com.example.characterchat.ai.AiClient;
import com.example.characterchat.ai.AiClientException;
import com.example.characterchat.ai.AiMultimodalRequest;
import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityMention;
import com.example.characterchat.analysis.entity.domain.EntityType;
import com.example.characterchat.analysis.entity.persistence.EntityCandidateMapper;
import com.example.characterchat.analysis.image.api.ImageFactResponse;
import com.example.characterchat.analysis.image.persistence.ImageAnalysisMapper;
import com.example.characterchat.book.domain.Book;
import com.example.characterchat.book.domain.BookImage;
import com.example.characterchat.book.domain.BookPage;
import com.example.characterchat.book.domain.BookParagraph;
import com.example.characterchat.book.persistence.BookMapper;
import com.example.characterchat.common.exception.BookNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ImageAnalysisService {
	private static final Set<String> FACT_TYPES = Set.of("APPEARANCE", "CLOTHING", "EXPRESSION", "ACTION",
			"LOCATION", "INDOOR_OUTDOOR", "WEATHER", "TIME_OF_DAY", "OBJECT");
	private static final Set<String> SOURCE_TYPES = Set.of("IMAGE", "TEXT_AND_IMAGE");
	private static final Set<String> STATUSES = Set.of("CONFIRMED", "CANDIDATE", "UNKNOWN", "CONFLICT");
	private static final String SYSTEM_PROMPT = """
			당신은 책 삽화를 원문과 함께 분석하는 관찰자입니다.
			이미지에서 실제로 보이는 정보와 같은 페이지 원문이 함께 지지하는 정보를 구분하세요.
			관찰 대상은 인물·장소·물건·조직과 외모, 옷차림, 표정, 행동, 장소, 실내외, 날씨, 시간대, 물건입니다.
			entityType은 CHARACTER, PLACE, OBJECT, ORGANIZATION 중 하나만 사용하세요.
			factType은 APPEARANCE, CLOTHING, EXPRESSION, ACTION, LOCATION, INDOOR_OUTDOOR, WEATHER, TIME_OF_DAY, OBJECT 중 하나만 사용하세요.
			sourceType은 IMAGE 또는 TEXT_AND_IMAGE만 사용하세요.
			status는 CONFIRMED, CANDIDATE, UNKNOWN, CONFLICT 중 하나만 사용하세요. OBSERVED 같은 다른 값은 사용하지 마세요.
			관계, 사건, 보이지 않는 감정·동기·성격은 추론하지 마세요.
			기존 후보와 확실히 같은 경우에만 matchedCandidateName에 제공된 정규 이름을 정확히 넣으세요.
			이름을 특정할 수 없는 시각 개체는 entities에 넣지 마세요.
			사실의 주체를 특정할 수 없으면 fact의 subjectName을 빈 문자열로 두고 CANDIDATE 또는 UNKNOWN을 사용하세요.
			'다른 동물', '새들', '여러 사람' 같은 불특정 집합 표현도 subjectName에 넣지 말고 빈 문자열로 두세요.
			입력 이미지 순서와 imageOrder가 일치해야 합니다.
			""";

	private final AiClient aiClient;
	private final BookMapper bookMapper;
	private final EntityCandidateMapper candidateMapper;
	private final ImageAnalysisMapper imageMapper;
	private final ImageAnalysisWriter writer;
	private final Path inputRoot;

	public ImageAnalysisService(AiClient aiClient, BookMapper bookMapper, EntityCandidateMapper candidateMapper,
			ImageAnalysisMapper imageMapper, ImageAnalysisWriter writer,
			@Value("${book-input.root-directory:../test-books}") String inputRoot) {
		this.aiClient = aiClient;
		this.bookMapper = bookMapper;
		this.candidateMapper = candidateMapper;
		this.imageMapper = imageMapper;
		this.writer = writer;
		this.inputRoot = Path.of(inputRoot).toAbsolutePath().normalize();
	}

	public List<ImageFactResponse> analyze(Long bookId) {
		Book book = requireBook(bookId);
		List<BookPage> pages = bookMapper.findPagesByBookId(bookId);
		List<BookParagraph> paragraphs = bookMapper.findParagraphsByBookId(bookId);
		List<BookImage> images = bookMapper.findImagesByBookId(bookId);
		List<EntityCandidate> candidates = candidateMapper.findCandidatesByBookId(bookId);
		List<EntityMention> mentions = candidateMapper.findMentionsByBookId(bookId);
		if (images.isEmpty()) throw new ImageAnalysisException("분석할 이미지가 없습니다.");

		CandidateIndex candidateIndex = new CandidateIndex(candidates, mentions);
		List<VisualEntityDraft> entityDrafts = new ArrayList<>();
		List<VisualFactDraft> factDrafts = new ArrayList<>();
		Map<Long, List<BookParagraph>> paragraphsByPage = paragraphs.stream().collect(Collectors.groupingBy(BookParagraph::pageId));
		Map<Long, List<BookImage>> imagesByPage = images.stream().collect(Collectors.groupingBy(BookImage::pageId));

		try {
			for (BookPage page : pages) {
				List<BookImage> pageImages = imagesByPage.getOrDefault(page.getId(), List.of());
				if (pageImages.isEmpty()) continue;
				List<Path> paths = pageImages.stream().map(this::resolveImagePath).toList();
				ImageAnalysisAiResponse response = aiClient.analyzeImagesStructured(new AiMultimodalRequest(
						SYSTEM_PROMPT, buildPrompt(book, page, paragraphsByPage.getOrDefault(page.getId(), List.of()),
								pageImages, candidateIndex), paths, AiMultimodalRequest.ImageDetail.LOW),
						ImageAnalysisAiResponse.class);
				validateAndAccumulate(response, pageImages, candidateIndex, entityDrafts, factDrafts);
			}
		} catch (AiClientException exception) {
			throw new ImageAnalysisException("AI 이미지 분석 호출에 실패했습니다.", exception);
		}

		writer.replace(bookId, entityDrafts, factDrafts);
		return getFacts(bookId);
	}

	@Transactional(readOnly = true)
	public List<ImageFactResponse> getFacts(Long bookId) {
		requireBook(bookId);
		return imageMapper.findFactsByBookId(bookId).stream().map(ImageFactResponse::from).toList();
	}

	private Book requireBook(Long bookId) {
		Book book = bookMapper.findBookById(bookId);
		if (book == null) throw new BookNotFoundException(bookId);
		return book;
	}

	private Path resolveImagePath(BookImage image) {
		try {
			Path realRoot = inputRoot.toRealPath();
			Path path = realRoot.resolve(image.filePath()).normalize().toRealPath();
			if (!path.startsWith(realRoot)) throw new ImageAnalysisException("입력 루트 밖의 이미지 경로입니다: " + image.filePath());
			return path;
		} catch (IOException exception) {
			throw new ImageAnalysisException("이미지 파일을 읽을 수 없습니다: " + image.filePath(), exception);
		}
	}

	private String buildPrompt(Book book, BookPage page, List<BookParagraph> paragraphs,
			List<BookImage> images, CandidateIndex candidates) {
		String text = paragraphs.stream().map(p -> "[sourceOrder=" + p.sourceOrder() + "] " + p.content())
				.collect(Collectors.joining("\n"));
		String imageOrders = images.stream().map(image -> Integer.toString(image.imageOrder())).collect(Collectors.joining(", "));
		return "책 제목: " + book.getTitle() + "\n페이지: " + page.getPageNumber()
				+ "\n첨부 이미지의 imageOrder 순서: " + imageOrders
				+ "\n\n같은 페이지 원문:\n" + text
				+ "\n\n현재 개체 후보:\n" + candidates.promptText();
	}

	private void validateAndAccumulate(ImageAnalysisAiResponse response, List<BookImage> images,
			CandidateIndex candidates, List<VisualEntityDraft> entityDrafts, List<VisualFactDraft> factDrafts) {
		if (response == null || response.entities == null || response.facts == null) {
			throw new ImageAnalysisException("AI 이미지 분석 응답에 entities 또는 facts가 없습니다.");
		}
		Map<Integer, BookImage> byOrder = images.stream().collect(Collectors.toMap(BookImage::imageOrder, Function.identity()));
		Map<String, String> pageSubjects = new HashMap<>();

		for (ImageAnalysisAiResponse.VisualEntity entity : response.entities) {
			BookImage image = requireImage(byOrder, entity.imageOrder);
			String observedName = entity.observedName == null ? "" : entity.observedName.strip();
			String matchedName = entity.matchedCandidateName == null ? "" : entity.matchedCandidateName.strip();
			if (observedName.isBlank() && matchedName.isBlank()) continue;
			EntityType type = parseType(entity.entityType);
			String description = requireText(entity.description, "entity description");
			validateConfidence(entity.confidence);
			EntityCandidate matched = null;
			if (!matchedName.isBlank()) {
				matched = candidates.find(matchedName);
				if (matched == null) throw new ImageAnalysisException("존재하지 않는 matchedCandidateName입니다: " + matchedName);
				if (matched.getEntityType() != type) throw new ImageAnalysisException("기존 후보와 entityType이 다릅니다: " + matchedName);
				if (observedName.isBlank()) observedName = matched.getCanonicalName();
			} else {
				EntityCandidate byObservedName = candidates.find(observedName);
				if (byObservedName != null && byObservedName.getEntityType() == type) matched = byObservedName;
			}
			String key = matched == null ? "image:" + image.id() + ":" + type + ":" + normalize(observedName)
					: "existing:" + matched.getId();
			String sourceType = matched == null ? "IMAGE" : "TEXT_AND_IMAGE";
			entityDrafts.add(new VisualEntityDraft(key, matched == null ? null : matched.getId(), type, observedName,
					description, entity.confidence, image.id(), sourceType));
			pageSubjects.put(normalize(observedName), key);
			if (matched != null) pageSubjects.put(normalize(matched.getCanonicalName()), key);
		}

		for (ImageAnalysisAiResponse.VisualFact fact : response.facts) {
			BookImage image = requireImage(byOrder, fact.imageOrder);
			String factType = allowed(fact.factType, FACT_TYPES, "factType");
			String sourceType = allowed(fact.sourceType, SOURCE_TYPES, "sourceType");
			String status = allowed(fact.status, STATUSES, "status");
			String value = requireText(fact.value, "fact value");
			validateConfidence(fact.confidence);
			List<String> subjectKeys = resolveSubjectKeys(fact.subjectName, pageSubjects, candidates);
			for (String subjectKey : subjectKeys) {
				factDrafts.add(new VisualFactDraft(subjectKey, factType, value, sourceType, status,
						fact.confidence, image.id(), fact.description == null ? "" : fact.description.strip()));
			}
		}
	}

	private List<String> resolveSubjectKeys(String subjectName, Map<String, String> pageSubjects, CandidateIndex candidates) {
		if (subjectName == null || subjectName.isBlank()) return java.util.Collections.singletonList(null);
		String direct = resolveSubjectKey(subjectName, pageSubjects, candidates);
		if (direct != null) return List.of(direct);

		List<String> parts = java.util.Arrays.stream(subjectName.strip().split("\\s*(?:와|과|및|그리고|,|·|&|/)\\s*"))
				.filter(part -> !part.isBlank()).toList();
		if (parts.size() < 2) return java.util.Collections.singletonList(null);
		List<String> keys = parts.stream().map(part -> resolveSubjectKey(part, pageSubjects, candidates)).toList();
		if (keys.stream().anyMatch(java.util.Objects::isNull)) {
			return java.util.Collections.singletonList(null);
		}
		return keys.stream().distinct().toList();
	}

	private String resolveSubjectKey(String subjectName, Map<String, String> pageSubjects, CandidateIndex candidates) {
		String subjectKey = pageSubjects.get(normalize(subjectName));
		if (subjectKey != null) return subjectKey;
		EntityCandidate candidate = candidates.find(subjectName);
		return candidate == null ? null : "existing:" + candidate.getId();
	}

	private BookImage requireImage(Map<Integer, BookImage> images, int imageOrder) {
		BookImage image = images.get(imageOrder);
		if (image == null) throw new ImageAnalysisException("현재 페이지에 없는 imageOrder입니다: " + imageOrder);
		return image;
	}

	private EntityType parseType(String value) {
		try { return EntityType.valueOf(requireText(value, "entityType").toUpperCase(Locale.ROOT)); }
		catch (IllegalArgumentException exception) { throw new ImageAnalysisException("허용되지 않은 entityType입니다: " + value); }
	}

	private String allowed(String value, Set<String> allowed, String field) {
		String normalized = requireText(value, field).toUpperCase(Locale.ROOT);
		if (!allowed.contains(normalized)) throw new ImageAnalysisException("허용되지 않은 " + field + "입니다: " + value);
		return normalized;
	}

	private String requireText(String value, String field) {
		if (value == null || value.isBlank()) throw new ImageAnalysisException("AI 응답의 " + field + "가 비어 있습니다.");
		return value.strip();
	}

	private void validateConfidence(double value) {
		if (!Double.isFinite(value) || value < 0 || value > 1) throw new ImageAnalysisException("confidence는 0부터 1 사이여야 합니다: " + value);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
	}

	private static final class CandidateIndex {
		private final List<EntityCandidate> candidates;
		private final Map<String, EntityCandidate> byName = new HashMap<>();
		private final Map<Long, List<String>> aliases;

		CandidateIndex(List<EntityCandidate> candidates, List<EntityMention> mentions) {
			this.candidates = candidates;
			this.aliases = mentions.stream().collect(Collectors.groupingBy(EntityMention::entityCandidateId,
					Collectors.mapping(EntityMention::mentionText, Collectors.toList())));
			for (EntityCandidate candidate : candidates) {
				byName.putIfAbsent(normalize(candidate.getCanonicalName()), candidate);
				aliases.getOrDefault(candidate.getId(), List.of()).forEach(alias -> byName.putIfAbsent(normalize(alias), candidate));
			}
		}

		EntityCandidate find(String name) { return byName.get(normalize(name)); }

		String promptText() {
			if (candidates.isEmpty()) return "없음";
			return candidates.stream().map(candidate -> "- " + candidate.getEntityType() + ": " + candidate.getCanonicalName()
					+ " (별칭: " + String.join(", ", aliases.getOrDefault(candidate.getId(), List.of())) + ")")
					.collect(Collectors.joining("\n"));
		}
	}
}
