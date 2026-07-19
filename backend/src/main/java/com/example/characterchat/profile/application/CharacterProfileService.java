package com.example.characterchat.profile.application;

import com.example.characterchat.ai.AiClient;
import com.example.characterchat.ai.AiClientException;
import com.example.characterchat.ai.AiTextRequest;
import com.example.characterchat.analysis.image.domain.ExtractedFact;
import com.example.characterchat.analysis.image.persistence.ImageAnalysisMapper;
import com.example.characterchat.analysis.kg.domain.EventParticipant;
import com.example.characterchat.analysis.kg.domain.KnowledgeEntity;
import com.example.characterchat.analysis.kg.domain.KnowledgeRelation;
import com.example.characterchat.analysis.kg.domain.StoryEvent;
import com.example.characterchat.analysis.kg.persistence.KnowledgeGraphMapper;
import com.example.characterchat.book.domain.Book;
import com.example.characterchat.book.domain.BookImage;
import com.example.characterchat.book.domain.BookPage;
import com.example.characterchat.book.domain.BookParagraph;
import com.example.characterchat.book.persistence.BookMapper;
import com.example.characterchat.common.exception.BookNotFoundException;
import com.example.characterchat.profile.api.CharacterProfileResponse;
import com.example.characterchat.profile.domain.CharacterProfile;
import com.example.characterchat.profile.persistence.CharacterProfileMapper;
import com.example.characterchat.review.domain.CharacterRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CharacterProfileService {
	private static final Set<String> FIELDS = Set.of("ROLE_DESCRIPTION", "APPEARANCE", "PERSONALITY", "VALUES",
			"GOALS", "SPEECH_STYLE", "MAJOR_EXPERIENCES", "ATTITUDES_TOWARD_OTHERS", "KNOWN_FACTS");
	private static final Set<String> SOURCE_TYPES = Set.of("TEXT", "IMAGE", "TEXT_AND_IMAGE", "INFERRED");
	private static final Set<String> INFERENCE_TYPES = Set.of("EXPLICIT", "INFERRED");
	private static final String SYSTEM_PROMPT = """
			검수된 한 등장인물의 결말 직후(AFTER_FINAL_EVENT) 최종 프로필을 생성하세요.
			역할, 지속적으로 확인되는 외모, 성격, 가치관, 목표, 말투, 주요 경험, 타인에 대한 태도와 알고 있는 사실을 작성하세요.
			한 장면의 표정이나 옷차림을 지속적 특성으로 단정하지 마세요.
			원문이나 이미지에서 직접 확인되면 EXPLICIT, 여러 근거를 조심스럽게 종합한 경우만 INFERRED를 사용하세요.
			근거가 없으면 꾸며내지 말고 '확인할 수 없음'이라고 작성하세요.
			모든 evidence는 허용된 profileField를 사용하고 sourceOrder 또는 pageNumber+imageOrder로 실제 근거를 지정하세요.
			profileField는 ROLE_DESCRIPTION, APPEARANCE, PERSONALITY, VALUES, GOALS, SPEECH_STYLE, MAJOR_EXPERIENCES, ATTITUDES_TOWARD_OTHERS, KNOWN_FACTS 중 하나만 사용하세요.
			sourceType은 TEXT, IMAGE, TEXT_AND_IMAGE, INFERRED 중 하나만 사용하고 inferenceType은 EXPLICIT 또는 INFERRED만 사용하세요.
			대화용 systemPrompt는 캐릭터의 1인칭, 결말 직후 시점, 근거 없는 사실을 모른다고 답하는 규칙을 포함하세요.
			""";

	private final AiClient aiClient; private final BookMapper bookMapper; private final ImageAnalysisMapper imageMapper;
	private final KnowledgeGraphMapper kgMapper; private final CharacterProfileMapper profileMapper; private final CharacterProfileWriter writer;

	public CharacterProfileService(AiClient aiClient, BookMapper bookMapper, ImageAnalysisMapper imageMapper,
			KnowledgeGraphMapper kgMapper, CharacterProfileMapper profileMapper, CharacterProfileWriter writer) {
		this.aiClient=aiClient; this.bookMapper=bookMapper; this.imageMapper=imageMapper;
		this.kgMapper=kgMapper; this.profileMapper=profileMapper; this.writer=writer;
	}

	public CharacterProfileResponse generate(Long bookId) {
		Book book = requireBook(bookId);
		CharacterRecord character = profileMapper.findChatEnabledCharacterByBookId(bookId);
		if (character == null) throw new CharacterProfileException("대화 가능 Character를 먼저 선택해야 합니다.");
		List<BookParagraph> paragraphs = bookMapper.findParagraphsByBookId(bookId);
		List<BookPage> pages = bookMapper.findPagesByBookId(bookId);
		List<BookImage> images = bookMapper.findImagesByBookId(bookId);
		List<KnowledgeEntity> entities = kgMapper.findEntitiesByBookId(bookId);
		try {
			ProfileGenerationAiResponse response = aiClient.generateStructured(new AiTextRequest(SYSTEM_PROMPT,
					buildPrompt(book, character, paragraphs, pages, images, entities)), ProfileGenerationAiResponse.class);
			List<ProfileEvidenceDraft> evidence = validateEvidence(response, paragraphs, pages, images);
			CharacterProfile profile = profile(character, response);
			writer.replace(bookId, profile, evidence);
			return get(bookId);
		} catch (AiClientException exception) {
			throw new CharacterProfileException("AI 캐릭터 프로필 생성에 실패했습니다.", exception);
		}
	}

	public CharacterProfileResponse get(Long bookId) {
		requireBook(bookId);
		CharacterProfile profile = profileMapper.findProfileByBookId(bookId);
		if (profile == null) throw new ProfileNotFoundException(bookId);
		return CharacterProfileResponse.from(profile, profileMapper.findEvidenceByProfileId(profile.getId()));
	}

	private String buildPrompt(Book book, CharacterRecord character, List<BookParagraph> paragraphs,
			List<BookPage> pages, List<BookImage> images, List<KnowledgeEntity> entities) {
		Map<Long, String> entityNames = entities.stream().collect(Collectors.toMap(KnowledgeEntity::getId, KnowledgeEntity::getName));
		Map<Long, Integer> pageNumbers = pages.stream().collect(Collectors.toMap(BookPage::getId, BookPage::getPageNumber));
		Map<Long, String> imageLocations = images.stream().collect(Collectors.toMap(BookImage::id,
				image -> "pageNumber=" + pageNumbers.get(image.pageId()) + ", imageOrder=" + image.imageOrder()));
		String text = paragraphs.stream().map(p -> "[sourceOrder=" + p.sourceOrder() + "] " + p.content()).collect(Collectors.joining("\n\n"));
		String facts = imageMapper.findFactsByBookId(book.getId()).stream()
				.filter(f -> f.getSubjectCandidateId() == null || f.getSubjectCandidateId().equals(character.candidateId()))
				.map(f -> "- " + f.getFactType() + ": " + f.getValue() + " (" + imageLocations.get(f.getImageId()) + ")")
				.collect(Collectors.joining("\n"));
		List<StoryEvent> events = kgMapper.findEventsByBookId(book.getId());
		List<EventParticipant> participants = kgMapper.findParticipantsByBookId(book.getId());
		String eventText = events.stream().filter(e -> participants.stream().anyMatch(p -> p.eventId().equals(e.getId())
				&& p.knowledgeEntityId().equals(character.knowledgeEntityId())))
				.map(e -> "- " + e.getName() + ": " + e.getDescription()).collect(Collectors.joining("\n"));
		String relationText = kgMapper.findRelationsByBookId(book.getId()).stream()
				.filter(r -> r.sourceEntityId().equals(character.knowledgeEntityId()) || r.targetEntityId().equals(character.knowledgeEntityId()))
				.map(r -> "- " + entityNames.get(r.sourceEntityId()) + " " + r.relationType() + " "
						+ entityNames.get(r.targetEntityId()) + ": " + r.description()).collect(Collectors.joining("\n"));
		return "책: " + book.getTitle() + "\n대상: " + character.name() + "\n최종 역할: " + character.narrativeRole()
				+ "\n별칭: " + String.join(", ", profileMapper.findAliasesByCharacterId(character.id()))
				+ "\n\n관련 이미지 사실:\n" + empty(facts) + "\n\n참여 사건:\n" + empty(eventText)
				+ "\n\n직접 관계:\n" + empty(relationText) + "\n\n전체 원문:\n" + text;
	}

	private List<ProfileEvidenceDraft> validateEvidence(ProfileGenerationAiResponse response,
			List<BookParagraph> paragraphs, List<BookPage> pages, List<BookImage> images) {
		if (response == null || response.evidence == null || response.evidence.isEmpty()) throw new CharacterProfileException("프로필 근거가 없습니다.");
		Map<Integer, BookParagraph> paragraphMap = paragraphs.stream().collect(Collectors.toMap(BookParagraph::sourceOrder, Function.identity()));
		Map<Long, Integer> pageNumbers = pages.stream().collect(Collectors.toMap(BookPage::getId, BookPage::getPageNumber));
		Map<String, BookImage> imageMap = images.stream().collect(Collectors.toMap(i -> pageNumbers.get(i.pageId()) + ":" + i.imageOrder(), Function.identity()));
		List<ProfileEvidenceDraft> result = new ArrayList<>();
		for (ProfileGenerationAiResponse.Evidence value : response.evidence) {
			String field = allowed(value.profileField, FIELDS, "profileField");
			String sourceType = allowed(value.sourceType, SOURCE_TYPES, "sourceType");
			String inferenceType = allowed(value.inferenceType, INFERENCE_TYPES, "inferenceType");
			Long paragraphId = value.sourceOrder > 0 && paragraphMap.get(value.sourceOrder) != null ? paragraphMap.get(value.sourceOrder).id() : null;
			Long imageId = value.pageNumber > 0 && imageMap.get(value.pageNumber + ":" + value.imageOrder) != null
					? imageMap.get(value.pageNumber + ":" + value.imageOrder).id() : null;
			if (value.sourceOrder > 0 && paragraphId == null) throw new CharacterProfileException("없는 sourceOrder입니다: " + value.sourceOrder);
			if ((value.pageNumber > 0 || value.imageOrder > 0) && imageId == null) throw new CharacterProfileException("없는 이미지 근거입니다.");
			if (paragraphId == null && imageId == null) throw new CharacterProfileException("프로필 근거 좌표가 필요합니다.");
			if ("TEXT".equals(sourceType) && paragraphId == null || "IMAGE".equals(sourceType) && imageId == null
					|| "TEXT_AND_IMAGE".equals(sourceType) && (paragraphId == null || imageId == null))
				throw new CharacterProfileException("sourceType과 근거 좌표가 일치하지 않습니다: " + sourceType);
			confidence(value.confidence);
			result.add(new ProfileEvidenceDraft(field, paragraphId, imageId, sourceType, inferenceType,
					required(value.description, "evidence description"), value.confidence));
		}
		return result;
	}

	private CharacterProfile profile(CharacterRecord character, ProfileGenerationAiResponse r) {
		CharacterProfile p = new CharacterProfile(); p.setCharacterId(character.id()); p.setStoryPoint("AFTER_FINAL_EVENT");
		p.setRoleDescription(required(r.roleDescription,"roleDescription")); p.setAppearance(required(r.appearance,"appearance"));
		p.setPersonality(required(r.personality,"personality")); p.setValues(required(r.values,"values"));
		p.setGoals(required(r.goals,"goals")); p.setSpeechStyle(required(r.speechStyle,"speechStyle"));
		p.setMajorExperiences(required(r.majorExperiences,"majorExperiences"));
		p.setAttitudesTowardOthers(required(r.attitudesTowardOthers,"attitudesTowardOthers"));
		p.setKnownFacts(required(r.knownFacts,"knownFacts")); p.setSystemPrompt(required(r.systemPrompt,"systemPrompt")); return p;
	}

	private Book requireBook(Long id) { Book b=bookMapper.findBookById(id); if(b==null) throw new BookNotFoundException(id); return b; }
	private String empty(String value) { return value == null || value.isBlank() ? "없음" : value; }
	private String required(String value,String field) { if(value==null||value.isBlank()) throw new CharacterProfileException(field+"가 비어 있습니다."); return value.strip(); }
	private String allowed(String value,Set<String> allowed,String field) {
		String v=required(value,field).replaceAll("([a-z0-9])([A-Z])","$1_$2").toUpperCase(Locale.ROOT);
		if(!allowed.contains(v)) throw new CharacterProfileException("허용되지 않은 "+field+"입니다: "+value); return v;
	}
	private void confidence(double v) { if(!Double.isFinite(v)||v<0||v>1) throw new CharacterProfileException("confidence는 0부터 1 사이여야 합니다."); }
}
