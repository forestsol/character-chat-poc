package com.example.characterchat.chat.application;

import com.example.characterchat.ai.AiClient;
import com.example.characterchat.ai.AiClientException;
import com.example.characterchat.ai.AiTextRequest;
import com.example.characterchat.chat.api.ChatResponse;
import com.example.characterchat.chat.domain.DirectKnowledgeRelation;
import com.example.characterchat.chat.persistence.ChatMapper;
import com.example.characterchat.profile.domain.CharacterProfile;
import com.example.characterchat.profile.persistence.CharacterProfileMapper;
import com.example.characterchat.rag.api.RagSearchResponse;
import com.example.characterchat.rag.application.RagService;
import com.example.characterchat.review.domain.CharacterRecord;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ChatService {
	private static final int MAX_QUESTION_LENGTH = 1000;
	private static final String UNKNOWN_ANSWER = "그건 내가 아는 이야기 안에서는 확인할 수 없어.";
	private static final String SYSTEM_RULES = """
		당신은 제공된 캐릭터 프로필의 인물로 답합니다.
		반드시 캐릭터의 1인칭과 결말 직후 시점을 유지하세요.
		오직 제공된 원문 문단과 직접 KG 관계로 뒷받침되는 사실만 답하세요.
		명시되지 않은 감정, 동기, 사건을 사실처럼 만들지 마세요.
		질문에 답할 충분한 근거가 없으면 supported=false로 반환하세요.
		supported=true이면 실제 사용한 paragraphId 또는 relationId를 하나 이상 반환하세요.
		usedParagraphIds와 usedRelationIds에는 제공된 ID만 넣으세요.
		""";

	private final AiClient aiClient;
	private final CharacterProfileMapper profileMapper;
	private final ChatMapper chatMapper;
	private final RagService ragService;

	public ChatService(AiClient aiClient, CharacterProfileMapper profileMapper, ChatMapper chatMapper, RagService ragService) {
		this.aiClient = aiClient;
		this.profileMapper = profileMapper;
		this.chatMapper = chatMapper;
		this.ragService = ragService;
	}

	public ChatResponse chat(Long bookId, String rawQuestion) {
		String question = validateQuestion(rawQuestion);
		CharacterRecord character = profileMapper.findChatEnabledCharacterByBookId(bookId);
		if (character == null) throw new ChatException("대화 가능 캐릭터를 먼저 선택해야 합니다.");
		CharacterProfile profile = profileMapper.findProfileByBookId(bookId);
		if (profile == null) throw new ChatException("캐릭터 프로필을 먼저 생성해야 합니다.");
		RagSearchResponse rag = ragService.search(bookId, question);
		List<DirectKnowledgeRelation> relations = chatMapper.findDirectRelations(bookId, character.knowledgeEntityId());
		try {
			ChatAiResponse ai = aiClient.generateStructured(new AiTextRequest(
					SYSTEM_RULES + "\n\n캐릭터 프로필 지침:\n" + profile.getSystemPrompt(),
					prompt(question, character, profile, rag, relations)), ChatAiResponse.class);
			ChatResponse response = validateAndBuild(bookId, character, profile, rag, relations, ai);
			chatMapper.updateBookStatus(bookId, "CHAT_READY");
			return response;
		} catch (ChatGenerationException exception) {
			throw exception;
		} catch (AiClientException exception) {
			throw new ChatGenerationException("AI 캐릭터 답변 생성에 실패했습니다.", exception);
		}
	}

	private ChatResponse validateAndBuild(Long bookId, CharacterRecord character, CharacterProfile profile,
	                                      RagSearchResponse rag, List<DirectKnowledgeRelation> relations, ChatAiResponse ai) {
		if (ai == null) throw new ChatGenerationException("AI 캐릭터 답변이 없습니다.");
		List<Long> paragraphIds = ai.usedParagraphIds == null ? List.of() : ai.usedParagraphIds.stream().distinct().toList();
		List<Long> relationIds = ai.usedRelationIds == null ? List.of() : ai.usedRelationIds.stream().distinct().toList();
		Set<Long> allowedParagraphs = new HashSet<>();
		rag.ranges().forEach(range -> range.paragraphs().forEach(p -> allowedParagraphs.add(p.paragraphId())));
		Set<Long> allowedRelations = relations.stream().map(DirectKnowledgeRelation::id).collect(java.util.stream.Collectors.toSet());
		if (!allowedParagraphs.containsAll(paragraphIds)) throw new ChatGenerationException("AI가 제공되지 않은 원문 문단을 근거로 선택했습니다.");
		if (!allowedRelations.containsAll(relationIds)) throw new ChatGenerationException("AI가 제공되지 않은 KG 관계를 근거로 선택했습니다.");
		if (ai.supported && paragraphIds.isEmpty() && relationIds.isEmpty())
			throw new ChatGenerationException("근거가 있다고 판단한 답변에는 원문 또는 KG 근거가 필요합니다.");
		String answer = ai.supported ? required(ai.answer) : UNKNOWN_ANSWER;
		List<Long> usedParagraphs = ai.supported ? paragraphIds : List.of();
		List<Long> usedRelations = ai.supported ? relationIds : List.of();
		return new ChatResponse(bookId,
				new ChatResponse.CharacterSummary(character.id(), character.name(), character.narrativeRole(), profile.getStoryPoint()),
				answer, ai.supported,
				new ChatResponse.Debug(usedParagraphs, usedRelations, rag.ranges(), relations));
	}

	private String prompt(String question, CharacterRecord character, CharacterProfile profile,
	                      RagSearchResponse rag, List<DirectKnowledgeRelation> relations) {
		StringBuilder value = new StringBuilder();
		value.append("질문: ").append(question).append("\n\n캐릭터: ").append(character.name())
				.append("\n이야기 시점: ").append(profile.getStoryPoint())
				.append("\n역할: ").append(profile.getRoleDescription())
				.append("\n성격: ").append(profile.getPersonality())
				.append("\n가치관: ").append(profile.getValues())
				.append("\n목표: ").append(profile.getGoals())
				.append("\n말투: ").append(profile.getSpeechStyle())
				.append("\n주요 경험: ").append(profile.getMajorExperiences())
				.append("\n알고 있는 사실: ").append(profile.getKnownFacts())
				.append("\n\n관련 원문:\n");
		rag.ranges().forEach(range -> range.paragraphs().forEach(p -> value.append("[paragraphId=").append(p.paragraphId())
				.append(", pageNumber=").append(p.pageNumber()).append(", sourceOrder=").append(p.sourceOrder())
				.append("] ").append(p.content()).append('\n')));
		value.append("\n캐릭터 직접 KG 관계:\n");
		if (relations.isEmpty()) value.append("없음\n");
		else relations.forEach(r -> value.append("[relationId=").append(r.id()).append("] ")
				.append(r.sourceName()).append(" --").append(r.relationType()).append("--> ")
				.append(r.targetName()).append(": ").append(r.description()).append('\n'));
		return value.toString();
	}

	private String validateQuestion(String value) {
		if (value == null || value.isBlank()) throw new ChatException("질문은 비어 있을 수 없습니다.");
		String stripped = value.strip();
		if (stripped.length() > MAX_QUESTION_LENGTH) throw new ChatException("질문은 1000자를 넘을 수 없습니다.");
		return stripped;
	}

	private String required(String value) {
		if (value == null || value.isBlank()) throw new ChatGenerationException("근거가 있는 AI 답변의 내용이 비어 있습니다.");
		return value.strip();
	}
}
