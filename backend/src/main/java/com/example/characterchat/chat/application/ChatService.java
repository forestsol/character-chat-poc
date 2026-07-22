package com.example.characterchat.chat.application;

import com.example.characterchat.ai.AiClient;
import com.example.characterchat.ai.AiClientException;
import com.example.characterchat.ai.AiTextRequest;
import com.example.characterchat.chat.api.ChatRequest;
import com.example.characterchat.chat.api.ChatResponse;
import com.example.characterchat.chat.domain.DirectKnowledgeRelation;
import com.example.characterchat.chat.persistence.ChatMapper;
import com.example.characterchat.profile.domain.CharacterProfile;
import com.example.characterchat.profile.domain.ProfileEvidence;
import com.example.characterchat.profile.persistence.CharacterProfileMapper;
import com.example.characterchat.rag.api.RagSearchResponse;
import com.example.characterchat.rag.application.RagService;
import com.example.characterchat.review.domain.CharacterRecord;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ChatService {
	private static final int MAX_QUESTION_LENGTH = 1000;
	private static final int MAX_HISTORY_MESSAGE_LENGTH = 2000;
	private static final int MAX_HISTORY_TOTAL_LENGTH = 10000;
	private static final int MAX_HISTORY_MESSAGES = 6;
	private static final String UNKNOWN_ANSWER = "글쎄, 그건 나도 잘 모르겠어.";
	private static final String SYSTEM_RULES = """
		당신은 제공된 캐릭터 프로필의 인물로 답합니다.
		반드시 캐릭터의 1인칭과 결말 직후 시점을 유지하세요.
		오직 제공된 원문 문단과 직접 KG 관계로 뒷받침되는 사실만 답하세요.
		명시되지 않은 감정, 동기, 사건을 사실처럼 만들지 마세요.
		질문을 직접 답하는 문장이 없어도 관련 행동, 대사, 사건이 있으면 supported=true로 반환하고 자연스러운 불확실성을 표현한 뒤 관련 행동이나 사건을 이어서 말하세요.
		이 경우 확인되지 않은 감정이나 동기는 단정하지 말고 캐릭터의 말로 짧게 불확실성을 표현하세요.
		관련 행동, 대사, 사건조차 제공되지 않은 경우에만 supported=false로 반환하세요.
		답변에서 '원문', '검색 결과', '근거', '확인할 수 없다', '정보가 없다' 같은 분석 시스템 표현을 사용하지 마세요.
		답변은 캐릭터의 말투로 자연스럽고 간결하게 1~4문장으로 작성하세요.
		supported=true이면 실제 사용한 paragraphId 또는 relationId를 하나 이상 반환하세요.
		usedParagraphIds와 usedRelationIds에는 제공된 ID만 넣으세요.
		캐릭터 프로필 근거를 사용했다면 usedProfileEvidenceIds에 제공된 profileEvidenceId를 넣으세요.
		INFERRED 프로필 근거는 성격과 행동에 일관된 선호·의향·가정 질문에 사용할 수 있습니다. 이때 명시된 사실처럼 단정하지 말고 '아마', '다시 기회가 있다면', '조금 망설여지지만'처럼 추론임이 자연스럽게 드러나게 답하세요.
		직접 답한 문장이 없더라도 질문과 관련된 성격, 가치관, 목표, 행동 근거가 있으면 supported=true로 답하고 결론을 회피하지 마세요.
		프로필 근거가 질문과 관련 없으면 사용하지 마세요. 성격과 행동으로도 뒷받침되지 않는 새로운 취향, 계획, 감정은 만들지 마세요.
		대화 기록은 대명사와 생략 표현을 이해하기 위한 문맥일 뿐 사실 근거가 아닙니다. 이전 캐릭터 답변의 내용을 새로운 사실의 근거로 사용하지 마세요.
		""";
	private static final String REWRITE_RULES = """
		당신은 대화의 현재 입력 의도를 판정하고, 사실 질문이면 검색에 사용할 독립 질문으로 정리합니다. 질문에 답하지 마세요.
		intent는 FACTUAL 또는 SOCIAL 중 하나만 사용하세요.
		책의 사건, 장소, 인물, 관계, 성격, 감정, 경험을 묻거나 확인하는 입력은 말투가 가벼워도 FACTUAL입니다.
		인사, 감사, 사과, 작별, 칭찬, 대화에 대한 짧은 반응처럼 책의 사실 확인이 전혀 필요 없는 입력만 SOCIAL입니다.
		SOCIAL이면 resolved=true이고 standaloneQuery, ambiguousReference, referentCandidates를 모두 비워 반환하세요.
		최근 대화는 대명사, 지시어, 생략된 대상과 시점을 해석할 때만 사용하세요.
		현재 질문이 이미 독립적이고 명확하면 표현을 바꾸지 말고 standaloneQuery에 그대로 반환하세요.
		'너', '네가', '너희'는 현재 대화 캐릭터를 가리킵니다. 독립 질문에서는 해당 캐릭터 이름으로 바꾸세요.
		질문에 '토끼', '여왕', '고양이'처럼 대상 명사가 직접 쓰였고 최근 대화에 같은 종류의 경쟁 대상이 없다면 명확한 질문으로 처리하세요.
		'어디더라', '뭐였지', '누구였지'는 기억을 되짚는 말투일 뿐, 이것만으로 모호하다고 판단하지 마세요.
		단순히 대상을 더 자세히 부를 수 있다는 이유로 resolved=false를 반환하지 마세요. 실제로 둘 이상의 대상이 가능할 때만 모호하다고 판단하세요.
		대화에 없는 사실을 추가하거나 질문의 범위를 넓히거나 좁히지 마세요.
		대상이 하나로 결정되면 resolved=true, 독립 질문, 빈 ambiguousReference, 빈 referentCandidates를 반환하세요.
		둘 이상의 대상이 가능하거나 하나로 정할 수 없으면 resolved=false, 빈 standaloneQuery, 모호한 표현, 가능한 후보 목록을 반환하세요.
		후보를 특정할 수 없다면 referentCandidates는 빈 목록으로 반환하세요.
		""";
	private static final String SOCIAL_RULES = """
		당신은 제공된 캐릭터 프로필의 인물로 일상 대화에 답합니다.
		캐릭터의 1인칭 시점과 말투를 유지하고 자연스럽게 1~2문장으로 답하세요.
		이 입력은 책의 사실 확인이 필요 없는 인사, 감사, 사과, 작별 또는 가벼운 반응입니다.
		책 속의 새로운 사건, 관계, 경험, 감정 또는 현재 행동을 만들어 덧붙이지 마세요.
		대화 기록과 이전 답변은 말의 흐름을 이해하는 문맥일 뿐 사실 근거가 아닙니다.
		supported=false, 빈 usedParagraphIds, 빈 usedRelationIds를 반환하세요.
		usedProfileEvidenceIds도 빈 목록으로 반환하세요.
		""";
	private static final String CLARIFICATION_RULES = """
		당신은 제공된 캐릭터 프로필의 인물로 답합니다.
		현재 질문의 대상이 모호하므로 사실에 답하지 말고, 사용자가 누구 또는 무엇을 뜻하는지 한 문장으로 되물으세요.
		후보가 있으면 후보를 자연스럽게 열거해 구체적으로 되묻고, 후보가 없으면 일반적으로 되물으세요.
		캐릭터의 1인칭 시점과 말투를 유지하세요. 분석 시스템 용어를 말하지 마세요.
		supported=false, 빈 usedParagraphIds, 빈 usedRelationIds를 반환하세요.
		usedProfileEvidenceIds도 빈 목록으로 반환하세요.
		대화 기록과 이전 답변은 사실 근거가 아닙니다.
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
		return chat(bookId, rawQuestion, List.of());
	}

	public ChatResponse chat(Long bookId, String rawQuestion, List<ChatRequest.HistoryMessage> rawHistory) {
		String question = validateQuestion(rawQuestion);
		List<ChatRequest.HistoryMessage> history = normalizeHistory(rawHistory);
		CharacterRecord character = profileMapper.findChatEnabledCharacterByBookId(bookId);
		if (character == null) throw new ChatException("대화 가능한 캐릭터를 먼저 선택해야 합니다.");
		CharacterProfile profile = profileMapper.findProfileByBookId(bookId);
		if (profile == null) throw new ChatException("캐릭터 프로필을 먼저 생성해야 합니다.");

		RewriteResult rewrite = rewriteQuestion(question, history, character.name());
		try {
		ChatResponse response;
		if (rewrite.intent().equals("SOCIAL")) response = social(bookId, question, history, rewrite, character, profile);
		else if (rewrite.resolved()) response = answer(bookId, question, history, rewrite, character, profile);
		else response = clarify(bookId, question, history, rewrite, character, profile);
			chatMapper.updateBookStatus(bookId, "CHAT_READY");
			return response;
		} catch (ChatGenerationException exception) {
			throw exception;
		} catch (AiClientException exception) {
			throw new ChatGenerationException("AI 캐릭터 응답 생성에 실패했습니다.", exception);
		}
	}

	private RewriteResult rewriteQuestion(String question, List<ChatRequest.HistoryMessage> history, String characterName) {
		try {
			ChatRewriteAiResponse ai = aiClient.generateStructured(new AiTextRequest(
					REWRITE_RULES, rewritePrompt(question, history, characterName)), ChatRewriteAiResponse.class);
			return validateRewrite(ai, question);
		} catch (AiClientException | RewriteValidationException exception) {
			return RewriteResult.fallback(question);
		}
	}

	private RewriteResult validateRewrite(ChatRewriteAiResponse ai, String originalQuestion) {
		if (ai == null) throw new RewriteValidationException();
		String intent = ai.intent == null ? "" : ai.intent.strip().toUpperCase(java.util.Locale.ROOT);
		if (!intent.equals("FACTUAL") && !intent.equals("SOCIAL")) throw new RewriteValidationException();
		List<String> candidates = cleanCandidates(ai.referentCandidates);
		if (intent.equals("SOCIAL")) {
			if (!ai.resolved || (ai.standaloneQuery != null && !ai.standaloneQuery.isBlank())
					|| (ai.ambiguousReference != null && !ai.ambiguousReference.isBlank()) || !candidates.isEmpty())
				throw new RewriteValidationException();
			return new RewriteResult("SOCIAL", true, true, false, "", "", List.of());
		}
		if (ai.resolved) {
			if (ai.standaloneQuery == null || ai.standaloneQuery.isBlank()) throw new RewriteValidationException();
			String standalone = ai.standaloneQuery.strip();
			if (standalone.length() > MAX_QUESTION_LENGTH) throw new RewriteValidationException();
			return new RewriteResult("FACTUAL", true, true, false, standalone, "", List.of());
		}
		String reference = ai.ambiguousReference == null ? "" : ai.ambiguousReference.strip();
		if (candidates.isEmpty() && isExplicitNamedReference(reference, originalQuestion)) {
			return new RewriteResult("FACTUAL", true, true, false, originalQuestion, "", List.of());
		}
		return new RewriteResult("FACTUAL", true, false, false, "", reference, candidates);
	}

	private ChatResponse social(Long bookId, String question, List<ChatRequest.HistoryMessage> history,
	                            RewriteResult rewrite, CharacterRecord character, CharacterProfile profile) {
		ChatAiResponse ai = aiClient.generateStructured(new AiTextRequest(
				SOCIAL_RULES + "\n\n캐릭터 프로필 지침\n" + profile.getSystemPrompt(),
				socialPrompt(question, history, character, profile)), ChatAiResponse.class);
		if (ai == null) throw new ChatGenerationException("AI 일상 대화 응답이 없습니다.");
		List<Long> paragraphIds = ai.usedParagraphIds == null ? List.of() : ai.usedParagraphIds;
		List<Long> relationIds = ai.usedRelationIds == null ? List.of() : ai.usedRelationIds;
		List<Long> profileEvidenceIds = ai.usedProfileEvidenceIds == null ? List.of() : ai.usedProfileEvidenceIds;
		if (ai.supported || !paragraphIds.isEmpty() || !relationIds.isEmpty() || !profileEvidenceIds.isEmpty())
			throw new ChatGenerationException("일상 대화 응답에는 사실 근거 ID를 사용할 수 없습니다.");
		return new ChatResponse(bookId, characterSummary(character, profile), required(ai.answer), false, "SOCIAL",
				new ChatResponse.Debug(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), rewrite.debug()));
	}

	private ChatResponse answer(Long bookId, String originalQuestion, List<ChatRequest.HistoryMessage> history,
	                            RewriteResult rewrite, CharacterRecord character, CharacterProfile profile) {
		RagSearchResponse rag = ragService.search(bookId, rewrite.standaloneQuery());
		List<DirectKnowledgeRelation> relations = chatMapper.findDirectRelations(bookId, character.knowledgeEntityId());
		List<ProfileEvidence> profileEvidence = profileMapper.findEvidenceByProfileId(profile.getId());
		ChatAiResponse ai = aiClient.generateStructured(new AiTextRequest(
				SYSTEM_RULES + "\n\n캐릭터 프로필 지침\n" + profile.getSystemPrompt(),
				answerPrompt(originalQuestion, history, rewrite.standaloneQuery(), character, profile, profileEvidence, rag, relations)),
				ChatAiResponse.class);
		return validateAndBuild(bookId, character, profile, profileEvidence, rag, relations, rewrite, ai);
	}

	private ChatResponse clarify(Long bookId, String question, List<ChatRequest.HistoryMessage> history,
	                             RewriteResult rewrite, CharacterRecord character, CharacterProfile profile) {
		ChatAiResponse ai = aiClient.generateStructured(new AiTextRequest(
				CLARIFICATION_RULES + "\n\n캐릭터 프로필 지침\n" + profile.getSystemPrompt(),
				clarificationPrompt(question, history, rewrite, character)), ChatAiResponse.class);
		String answer = required(ai == null ? null : ai.answer);
		return new ChatResponse(bookId, characterSummary(character, profile), answer, false, "CLARIFICATION",
				new ChatResponse.Debug(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), rewrite.debug()));
	}

	private ChatResponse validateAndBuild(Long bookId, CharacterRecord character, CharacterProfile profile,
	                                      List<ProfileEvidence> profileEvidence, RagSearchResponse rag,
	                                      List<DirectKnowledgeRelation> relations,
	                                      RewriteResult rewrite, ChatAiResponse ai) {
		if (ai == null) throw new ChatGenerationException("AI 캐릭터 응답이 없습니다.");
		List<Long> paragraphIds = ai.usedParagraphIds == null ? List.of() : ai.usedParagraphIds.stream().distinct().toList();
		List<Long> relationIds = ai.usedRelationIds == null ? List.of() : ai.usedRelationIds.stream().distinct().toList();
		List<Long> profileEvidenceIds = ai.usedProfileEvidenceIds == null ? List.of()
				: ai.usedProfileEvidenceIds.stream().distinct().toList();
		Set<Long> allowedParagraphs = new HashSet<>();
		rag.ranges().forEach(range -> range.paragraphs().forEach(p -> allowedParagraphs.add(p.paragraphId())));
		Set<Long> allowedRelations = relations.stream().map(DirectKnowledgeRelation::id).collect(java.util.stream.Collectors.toSet());
		Set<Long> allowedProfileEvidence = profileEvidence.stream().map(ProfileEvidence::id)
				.collect(java.util.stream.Collectors.toSet());
		if (!allowedParagraphs.containsAll(paragraphIds)) throw new ChatGenerationException("AI가 제공되지 않은 원문 문단을 근거로 선택했습니다.");
		if (!allowedRelations.containsAll(relationIds)) throw new ChatGenerationException("AI가 제공되지 않은 KG 관계를 근거로 선택했습니다.");
		if (!allowedProfileEvidence.containsAll(profileEvidenceIds))
			throw new ChatGenerationException("AI가 제공되지 않은 캐릭터 프로필 근거를 선택했습니다.");
		if (ai.supported && paragraphIds.isEmpty() && relationIds.isEmpty() && profileEvidenceIds.isEmpty())
			throw new ChatGenerationException("근거가 있다고 판단한 응답에는 원문, KG 또는 프로필 근거가 필요합니다.");
		String answer = ai.supported ? required(ai.answer) : UNKNOWN_ANSWER;
		List<Long> usedParagraphs = ai.supported ? paragraphIds : List.of();
		List<Long> usedRelations = ai.supported ? relationIds : List.of();
		List<Long> usedProfileEvidence = ai.supported ? profileEvidenceIds : List.of();
		return new ChatResponse(bookId, characterSummary(character, profile), answer, ai.supported,
				ai.supported ? "ANSWER" : "UNKNOWN",
				new ChatResponse.Debug(usedParagraphs, usedRelations, usedProfileEvidence,
						rag.ranges(), relations, profileEvidence, rewrite.debug()));
	}

	private ChatResponse.CharacterSummary characterSummary(CharacterRecord character, CharacterProfile profile) {
		return new ChatResponse.CharacterSummary(character.id(), character.name(), character.narrativeRole(), profile.getStoryPoint());
	}

	private String rewritePrompt(String question, List<ChatRequest.HistoryMessage> history, String characterName) {
		return "현재 대화 캐릭터: " + characterName + "\n최근 대화:\n" + formatHistory(history) + "\n현재 질문: " + question;
	}

	private String socialPrompt(String question, List<ChatRequest.HistoryMessage> history,
	                           CharacterRecord character, CharacterProfile profile) {
		return "캐릭터: " + character.name()
				+ "\n말투: " + profile.getSpeechStyle()
				+ "\n최근 대화(문맥 전용, 사실 근거 아님):\n" + formatHistory(history)
				+ "\n사용자의 현재 입력: " + question;
	}

	private boolean isExplicitNamedReference(String reference, String question) {
		if (reference.isBlank() || !question.contains(reference)) return false;
		String normalized = reference.replaceAll("\\s+", "");
		return !Set.of("그", "그녀", "그사람", "그분", "걔", "그아이", "그동물", "그것", "그거",
				"거기", "그곳", "그때", "저사람", "이사람", "저것", "이것").contains(normalized);
	}

	private String answerPrompt(String originalQuestion, List<ChatRequest.HistoryMessage> history, String standaloneQuery,
	                            CharacterRecord character, CharacterProfile profile, List<ProfileEvidence> profileEvidence,
	                            RagSearchResponse rag,
	                            List<DirectKnowledgeRelation> relations) {
		StringBuilder value = new StringBuilder();
		value.append("최근 대화(문맥 전용, 사실 근거 아님):\n").append(formatHistory(history))
				.append("\n사용자의 현재 질문: ").append(originalQuestion)
				.append("\n검색용으로 해석한 독립 질문: ").append(standaloneQuery)
				.append("\n현재 질문에 답하세요. 독립 질문은 해석과 검색을 위한 보조 정보일 뿐입니다.")
				.append("\n\n캐릭터: ").append(character.name())
				.append("\n이야기 시점: ").append(profile.getStoryPoint())
				.append("\n역할: ").append(profile.getRoleDescription())
				.append("\n성격: ").append(profile.getPersonality())
				.append("\n가치관: ").append(profile.getValues())
				.append("\n목표: ").append(profile.getGoals())
				.append("\n말투: ").append(profile.getSpeechStyle())
				.append("\n주요 경험: ").append(profile.getMajorExperiences())
				.append("\n알고 있는 사실: ").append(profile.getKnownFacts())
				.append("\n\n검증된 캐릭터 프로필 근거:\n");
		if (profileEvidence.isEmpty()) value.append("없음\n");
		else profileEvidence.forEach(e -> value.append("[profileEvidenceId=").append(e.id())
				.append(", profileField=").append(e.profileField())
				.append(", inferenceType=").append(e.inferenceType())
				.append(", confidence=").append(e.confidence())
				.append(", paragraphId=").append(e.paragraphId())
				.append(", imageId=").append(e.imageId()).append("] ")
				.append(e.description()).append('\n'));
		value
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

	private String clarificationPrompt(String question, List<ChatRequest.HistoryMessage> history,
	                                   RewriteResult rewrite, CharacterRecord character) {
		return "캐릭터: " + character.name() + "\n최근 대화:\n" + formatHistory(history)
				+ "\n현재 질문: " + question
				+ "\n모호한 표현: " + rewrite.ambiguousReference()
				+ "\n가능한 대상 후보: " + (rewrite.referentCandidates().isEmpty() ? "없음" : String.join(", ", rewrite.referentCandidates()));
	}

	private String formatHistory(List<ChatRequest.HistoryMessage> history) {
		if (history.isEmpty()) return "없음\n";
		StringBuilder value = new StringBuilder();
		history.forEach(message -> value.append(message.role()).append(": ").append(message.content()).append('\n'));
		return value.toString();
	}

	private List<ChatRequest.HistoryMessage> normalizeHistory(List<ChatRequest.HistoryMessage> rawHistory) {
		if (rawHistory == null || rawHistory.isEmpty()) return List.of();
		List<ChatRequest.HistoryMessage> validated = new ArrayList<>();
		for (ChatRequest.HistoryMessage message : rawHistory) {
			if (message == null || message.role() == null || message.content() == null || message.content().isBlank())
				throw new ChatException("대화 기록의 역할과 내용은 비어 있을 수 없습니다.");
			String role = message.role().strip().toUpperCase();
			if (!role.equals("USER") && !role.equals("ASSISTANT"))
				throw new ChatException("대화 기록 역할은 USER 또는 ASSISTANT여야 합니다.");
			String content = message.content().strip();
			if (content.length() > MAX_HISTORY_MESSAGE_LENGTH)
				throw new ChatException("과거 메시지는 하나당 2000자를 넘을 수 없습니다.");
			validated.add(new ChatRequest.HistoryMessage(role, content));
		}
		int start = Math.max(0, validated.size() - MAX_HISTORY_MESSAGES);
		List<ChatRequest.HistoryMessage> recent = validated.subList(start, validated.size());
		int total = 0;
		int keepFrom = recent.size();
		for (int i = recent.size() - 1; i >= 0; i--) {
			int next = total + recent.get(i).content().length();
			if (next > MAX_HISTORY_TOTAL_LENGTH) break;
			total = next;
			keepFrom = i;
		}
		return List.copyOf(recent.subList(keepFrom, recent.size()));
	}

	private List<String> cleanCandidates(List<String> values) {
		if (values == null) return List.of();
		return values.stream().filter(value -> value != null && !value.isBlank())
				.map(String::strip).distinct().limit(5).toList();
	}

	private String validateQuestion(String value) {
		if (value == null || value.isBlank()) throw new ChatException("질문은 비어 있을 수 없습니다.");
		String stripped = value.strip();
		if (stripped.length() > MAX_QUESTION_LENGTH) throw new ChatException("질문은 1000자를 넘을 수 없습니다.");
		return stripped;
	}

	private String required(String value) {
		if (value == null || value.isBlank()) throw new ChatGenerationException("AI 응답 내용이 비어 있습니다.");
		return value.strip();
	}

	private record RewriteResult(String intent, boolean attempted, boolean resolved, boolean fallback, String standaloneQuery,
	                             String ambiguousReference, List<String> referentCandidates) {
		static RewriteResult fallback(String question) {
			return new RewriteResult("FACTUAL", true, true, true, question, "", List.of());
		}

		ChatResponse.Rewrite debug() {
			return new ChatResponse.Rewrite(intent, attempted, resolved, fallback, standaloneQuery,
					ambiguousReference, referentCandidates);
		}
	}

	private static class RewriteValidationException extends RuntimeException { }
}
