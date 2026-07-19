package com.example.characterchat.ai.openai;

import com.example.characterchat.ai.AiMultimodalRequest;
import com.example.characterchat.ai.AiTextRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_OPENAI_LIVE_TESTS", matches = "true")
class OpenAiLiveIntegrationTests {

	private static OpenAiAiClient client;

	@BeforeAll
	static void createClient() {
		OpenAiProperties properties = new OpenAiProperties();
		properties.setApiKey(requiredEnvironmentVariable("OPENAI_API_KEY"));
		String configuredModel = System.getenv("OPENAI_MODEL");
		if (configuredModel != null && !configuredModel.isBlank()) {
			properties.setModel(configuredModel);
		}
		client = new OpenAiAiClient(new OpenAiConfiguration().openAIClient(properties), properties);
	}

	@Test
	void 텍스트_응답을_생성한다() {
		String response = client.generateText(new AiTextRequest(
				"한 문장으로만 답하세요.",
				"이상한 나라의 앨리스의 주인공 이름은 무엇인가요?"));

		assertThat(response).isNotBlank();
	}

	@Test
	void 구조화_응답을_생성한다() {
		CharacterResult response = client.generateStructured(new AiTextRequest(
				"질문에서 요구한 인물 정보를 구조화해 답하세요.",
				"이상한 나라의 앨리스의 주인공 이름과 역할을 알려주세요."), CharacterResult.class);

		assertThat(response.name).isNotBlank();
		assertThat(response.role).isNotBlank();
	}

	@Test
	void 로컬_이미지를_분석한다() {
		Path imagePath = Path.of("..", "test-books", "alice-demo", "images", "page-001-01.png");
		String response = client.analyzeImages(new AiMultimodalRequest(
				"짧게 답하세요.",
				"이 삽화에 보이는 장면을 한국어 한 문장으로 설명하세요.",
				List.of(imagePath),
				AiMultimodalRequest.ImageDetail.LOW));

		assertThat(response).isNotBlank();
	}

	private static String requiredEnvironmentVariable(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + " 환경 변수가 필요합니다.");
		}
		return value;
	}

	public static class CharacterResult {
		public String name;
		public String role;
	}
}
