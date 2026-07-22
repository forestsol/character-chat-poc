package com.example.characterchat.analysis.kg.application;

import com.example.characterchat.ai.AiClient;
import com.example.characterchat.ai.AiClientException;
import com.example.characterchat.ai.AiTextRequest;
import com.example.characterchat.analysis.entity.domain.EntityCandidate;
import com.example.characterchat.analysis.entity.domain.EntityMention;
import com.example.characterchat.analysis.entity.persistence.EntityCandidateMapper;
import com.example.characterchat.analysis.image.domain.ExtractedFact;
import com.example.characterchat.analysis.image.persistence.ImageAnalysisMapper;
import com.example.characterchat.analysis.kg.api.KnowledgeGraphResponse;
import com.example.characterchat.analysis.kg.persistence.KnowledgeGraphMapper;
import com.example.characterchat.book.domain.Book;
import com.example.characterchat.book.domain.BookImage;
import com.example.characterchat.book.domain.BookPage;
import com.example.characterchat.book.domain.BookParagraph;
import com.example.characterchat.book.persistence.BookMapper;
import com.example.characterchat.common.exception.BookNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KnowledgeGraphService {
	private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphService.class);
	private static final String SYSTEM_PROMPT = """
			당신은 책의 원문, 개체 후보와 이미지 관찰 사실에서 주요 사건과 직접 관계를 추출합니다.
			제공된 후보 이름만 사건 참여자와 관계의 주체·대상으로 사용하세요.
			관계의 sourceCandidateName과 targetCandidateName에는 후보 목록의 이름을 줄이거나 합치지 말고 정확히 하나씩 그대로 사용하세요.
			후보 목록에 없는 집합명, 일반명, 새 이름으로 관계를 만들지 마세요.
			사건은 이야기 순서대로 1부터 sequenceOrder를 부여하세요.
			관계 유형은 FOLLOWED, MET, POSSESSES 같은 의미의 대문자 스네이크 표기로 작성하세요.
			복잡한 추론, 성격 판단, 최단 경로와 다단계 관계는 만들지 마세요.
			각 사건, 참여자와 관계에는 최소 하나의 근거가 필요합니다.
			텍스트 근거는 sourceOrder를 쓰고 pageNumber와 imageOrder는 0으로 두세요.
			이미지 근거는 sourceOrder를 0으로 두고 pageNumber와 imageOrder를 모두 쓰세요.
			""";

	private final AiClient aiClient; private final BookMapper bookMapper; private final EntityCandidateMapper candidateMapper;
	private final ImageAnalysisMapper imageMapper; private final KnowledgeGraphMapper kgMapper; private final KnowledgeGraphWriter writer;

	public KnowledgeGraphService(AiClient aiClient, BookMapper bookMapper, EntityCandidateMapper candidateMapper,
			ImageAnalysisMapper imageMapper, KnowledgeGraphMapper kgMapper, KnowledgeGraphWriter writer) {
		this.aiClient = aiClient; this.bookMapper = bookMapper; this.candidateMapper = candidateMapper;
		this.imageMapper = imageMapper; this.kgMapper = kgMapper; this.writer = writer;
	}

	public KnowledgeGraphResponse build(Long bookId) {
		Book book = requireBook(bookId);
		List<BookParagraph> paragraphs = bookMapper.findParagraphsByBookId(bookId);
		List<BookPage> pages = bookMapper.findPagesByBookId(bookId);
		List<BookImage> images = bookMapper.findImagesByBookId(bookId);
		List<EntityCandidate> candidates = candidateMapper.findCandidatesByBookId(bookId);
		List<EntityMention> mentions = candidateMapper.findMentionsByBookId(bookId);
		List<ExtractedFact> facts = imageMapper.findFactsByBookId(bookId);
		if (candidates.isEmpty()) throw new KnowledgeGraphException("KG를 구축할 개체 후보가 없습니다.");
		CandidateIndex candidateIndex = new CandidateIndex(candidates, mentions);
		try {
			KgExtractionAiResponse response = aiClient.generateStructured(new AiTextRequest(SYSTEM_PROMPT,
					buildPrompt(book, paragraphs, pages, images, candidates, facts, candidateIndex)), KgExtractionAiResponse.class);
			ValidatedDraft drafts = validate(response, paragraphs, pages, images, candidateIndex);
			writer.replace(bookId, candidates, drafts.events, drafts.relations);
			return get(bookId);
		} catch (AiClientException exception) {
			throw new KnowledgeGraphException("AI 사건 및 관계 추출 호출에 실패했습니다.", exception);
		}
	}

	@Transactional(readOnly = true)
	public KnowledgeGraphResponse get(Long bookId) {
		requireBook(bookId);
		return KnowledgeGraphResponse.from(kgMapper.findEventsByBookId(bookId), kgMapper.findParticipantsByBookId(bookId),
				kgMapper.findEntitiesByBookId(bookId), kgMapper.findRelationsByBookId(bookId));
	}

	private Book requireBook(Long id) { Book book = bookMapper.findBookById(id); if (book == null) throw new BookNotFoundException(id); return book; }

	private String buildPrompt(Book book, List<BookParagraph> paragraphs, List<BookPage> pages, List<BookImage> images,
			List<EntityCandidate> candidates,
			List<ExtractedFact> facts, CandidateIndex index) {
		Map<Long, Integer> pageNumbers = pages.stream().collect(Collectors.toMap(BookPage::getId, BookPage::getPageNumber));
		Map<Long, String> imageLocations = images.stream().collect(Collectors.toMap(BookImage::id,
				image -> "pageNumber=" + pageNumbers.get(image.pageId()) + ", imageOrder=" + image.imageOrder()));
		String text = paragraphs.stream().map(p -> "[sourceOrder=" + p.sourceOrder() + "] " + p.content()).collect(Collectors.joining("\n\n"));
		String candidateText = candidates.stream().map(c -> "- " + c.getEntityType() + ": " + c.getCanonicalName()).collect(Collectors.joining("\n"));
		String factText = facts.isEmpty() ? "없음" : facts.stream().map(f -> "- " + f.getFactType() + " / "
				+ (f.getSubjectCandidateId() == null ? "주체 미정" : index.byId(f.getSubjectCandidateId()).getCanonicalName())
				+ " / " + f.getValue() + " / " + imageLocations.getOrDefault(f.getImageId(), "이미지 위치 미상"))
				.collect(Collectors.joining("\n"));
		return "책 제목: " + book.getTitle() + "\n\n개체 후보:\n" + candidateText
				+ "\n\n이미지 관찰 사실:\n" + factText + "\n\n전체 원문:\n" + text;
	}

	private ValidatedDraft validate(KgExtractionAiResponse response, List<BookParagraph> paragraphs,
			List<BookPage> pages, List<BookImage> images, CandidateIndex candidates) {
		if (response == null || response.events == null || response.relations == null) throw new KnowledgeGraphException("AI KG 응답에 events 또는 relations가 없습니다.");
		Map<Integer, BookParagraph> paragraphIndex = paragraphs.stream().collect(Collectors.toMap(BookParagraph::sourceOrder, Function.identity()));
		Map<Long, Integer> pageNumbers = pages.stream().collect(Collectors.toMap(BookPage::getId, BookPage::getPageNumber));
		Map<String, BookImage> imageIndex = images.stream().collect(Collectors.toMap(i -> pageNumbers.get(i.pageId()) + ":" + i.imageOrder(), Function.identity()));
		List<EventDraft> events = new ArrayList<>();
		for (KgExtractionAiResponse.Event event : response.events) {
			String name = required(event.name, "event name"); String description = required(event.description, "event description");
			if (event.sequenceOrder < 1) throw new KnowledgeGraphException("sequenceOrder는 1 이상이어야 합니다.");
			confidence(event.confidence); EvidenceRef evidence = evidence(event.evidence, paragraphIndex, imageIndex);
			if (event.participants == null || event.participants.isEmpty()) throw new KnowledgeGraphException(name + " 사건에 참여자가 없습니다.");
			List<ParticipantDraft> participants = new ArrayList<>(); Set<String> participantKeys = new HashSet<>();
			for (KgExtractionAiResponse.Participant participant : event.participants) {
				EntityCandidate candidate = candidates.find(required(participant.candidateName, "participant candidateName"));
				if (candidate == null) throw new KnowledgeGraphException("존재하지 않는 사건 참여자입니다: " + participant.candidateName);
				String role = required(participant.role, "participant role");
				if (participantKeys.add(candidate.getId() + ":" + role)) participants.add(new ParticipantDraft(candidate.getId(), role,
						evidence(participant.evidence, paragraphIndex, imageIndex)));
			}
			events.add(new EventDraft(name, description, event.sequenceOrder, event.confidence, evidence, participants));
		}
		List<RelationDraft> relations = new ArrayList<>();
		for (KgExtractionAiResponse.Relation relation : response.relations) {
			String sourceName = relation.sourceCandidateName == null ? "" : relation.sourceCandidateName.strip();
			String targetName = relation.targetCandidateName == null ? "" : relation.targetCandidateName.strip();
			EntityCandidate source = candidates.find(sourceName);
			EntityCandidate target = candidates.find(targetName);
			if (source == null || target == null) {
				log.warn("후보에 연결할 수 없는 KG 관계를 제외합니다: source='{}', target='{}'", sourceName, targetName);
				continue;
			}
			if (source.getId().equals(target.getId())) throw new KnowledgeGraphException("자기 자신을 향하는 관계는 저장하지 않습니다.");
			String type = required(relation.relationType, "relationType").toUpperCase(Locale.ROOT);
			if (!type.matches("[A-Z][A-Z0-9_]*")) throw new KnowledgeGraphException("relationType은 대문자 스네이크 표기여야 합니다: " + type);
			confidence(relation.confidence);
			relations.add(new RelationDraft(source.getId(), type, target.getId(), required(relation.description, "relation description"),
					relation.confidence, evidence(relation.evidence, paragraphIndex, imageIndex)));
		}
		return new ValidatedDraft(events, relations);
	}

	private EvidenceRef evidence(KgExtractionAiResponse.Evidence value, Map<Integer, BookParagraph> paragraphs, Map<String, BookImage> images) {
		if (value == null) throw new KnowledgeGraphException("근거가 없습니다.");
		Long paragraphId = null; Long imageId = null;
		if (value.sourceOrder > 0) { BookParagraph p = paragraphs.get(value.sourceOrder); if (p == null) throw new KnowledgeGraphException("없는 sourceOrder입니다: " + value.sourceOrder); paragraphId = p.id(); }
		if (value.pageNumber > 0 || value.imageOrder > 0) { BookImage i = images.get(value.pageNumber + ":" + value.imageOrder); if (i == null) throw new KnowledgeGraphException("없는 이미지 근거입니다: " + value.pageNumber + ":" + value.imageOrder); imageId = i.id(); }
		if (paragraphId == null && imageId == null) throw new KnowledgeGraphException("텍스트 또는 이미지 근거가 필요합니다.");
		return new EvidenceRef(paragraphId, imageId);
	}

	private String required(String value, String field) { if (value == null || value.isBlank()) throw new KnowledgeGraphException(field + "가 비어 있습니다."); return value.strip(); }
	private void confidence(double value) { if (!Double.isFinite(value) || value < 0 || value > 1) throw new KnowledgeGraphException("confidence는 0부터 1 사이여야 합니다."); }
	private static String normalize(String value) { return value == null ? "" : value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", ""); }

	private record ValidatedDraft(List<EventDraft> events, List<RelationDraft> relations) {}
	private static final class CandidateIndex {
		private final Map<String, EntityCandidate> names = new HashMap<>(); private final Map<Long, EntityCandidate> ids = new HashMap<>();
		CandidateIndex(List<EntityCandidate> candidates, List<EntityMention> mentions) {
			candidates.forEach(c -> { names.putIfAbsent(normalize(c.getCanonicalName()), c); ids.put(c.getId(), c); });
			mentions.forEach(m -> { EntityCandidate c = ids.get(m.entityCandidateId()); if (c != null) names.putIfAbsent(normalize(m.mentionText()), c); });
		}
		EntityCandidate find(String name) { return names.get(normalize(name)); }
		EntityCandidate byId(Long id) { return ids.get(id); }
	}
}
