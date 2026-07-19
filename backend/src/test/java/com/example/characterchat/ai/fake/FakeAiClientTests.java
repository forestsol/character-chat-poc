package com.example.characterchat.ai.fake;

import com.example.characterchat.ai.AiClientException;
import com.example.characterchat.ai.AiMultimodalRequest;
import com.example.characterchat.ai.AiTextRequest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FakeAiClientTests {

	private final FakeAiClient client = new FakeAiClient();

	@Test
	void 등록한_텍스트와_이미지_응답을_순서대로_반환한다() {
		client.enqueueTextResponse("텍스트 응답");
		client.enqueueImageResponse("이미지 응답");

		assertThat(client.generateText(new AiTextRequest("", "질문")))
				.isEqualTo("텍스트 응답");
		assertThat(client.analyzeImages(new AiMultimodalRequest(
				"", "이미지를 설명해 줘", List.of(Path.of("sample.png")), null)))
				.isEqualTo("이미지 응답");
	}

	@Test
	void 등록한_구조화_응답을_타입에_맞게_반환한다() {
		SampleResult expected = new SampleResult("Alice", 0.9);
		client.enqueueStructuredResponse(SampleResult.class, expected);

		SampleResult actual = client.generateStructured(
				new AiTextRequest("", "등장인물을 분석해 줘"), SampleResult.class);

		assertThat(actual).isSameAs(expected);
	}

	@Test
	void 준비된_응답이_없으면_명확한_예외를_던진다() {
		assertThatThrownBy(() -> client.generateText(new AiTextRequest("", "질문")))
				.isInstanceOf(AiClientException.class)
				.hasMessageContaining("Fake 텍스트 응답");
	}

	private record SampleResult(String name, double confidence) {
	}
}
