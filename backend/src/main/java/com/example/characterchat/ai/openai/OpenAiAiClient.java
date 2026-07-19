package com.example.characterchat.ai.openai;

import com.example.characterchat.ai.AiClient;
import com.example.characterchat.ai.AiClientException;
import com.example.characterchat.ai.AiMultimodalRequest;
import com.example.characterchat.ai.AiTextRequest;
import com.openai.client.OpenAIClient;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputImage;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAiAiClient implements AiClient {

	private final OpenAIClient client;
	private final OpenAiProperties properties;

	public OpenAiAiClient(OpenAIClient client, OpenAiProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	@Override
	public String generateText(AiTextRequest request) {
		try {
			ResponseCreateParams params = baseBuilder(request)
					.build();
			return extractText(client.responses().create(params));
		} catch (Exception exception) {
			throw translate("OpenAI 텍스트 생성에 실패했습니다.", exception);
		}
	}

	@Override
	public <T> T generateStructured(AiTextRequest request, Class<T> responseType) {
		try {
			StructuredResponseCreateParams<T> params = baseBuilder(request)
					.text(responseType)
					.build();
			StructuredResponse<T> response = client.responses().create(params);
			return response.output().stream()
					.flatMap(item -> item.message().stream())
					.flatMap(message -> message.content().stream())
					.flatMap(content -> content.outputText().stream())
					.findFirst()
					.orElseThrow(() -> new AiClientException("OpenAI 구조화 응답에 출력이 없습니다."));
		} catch (AiClientException exception) {
			throw exception;
		} catch (Exception exception) {
			throw translate("OpenAI 구조화 응답 생성에 실패했습니다.", exception);
		}
	}

	@Override
	public String analyzeImages(AiMultimodalRequest request) {
		try {
			ResponseInputItem.Message.Builder message = ResponseInputItem.Message.builder()
					.role(ResponseInputItem.Message.Role.USER)
					.addInputTextContent(request.userPrompt());
			for (Path imagePath : request.imagePaths()) {
				message.addContent(toImage(imagePath, request.imageDetail()));
			}
			ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
					.model(properties.getModel())
					.inputOfResponse(List.of(ResponseInputItem.ofMessage(message.build())));
			if (!request.systemPrompt().isBlank()) {
				builder.instructions(request.systemPrompt());
			}
			return extractText(client.responses().create(builder.build()));
		} catch (AiClientException exception) {
			throw exception;
		} catch (Exception exception) {
			throw translate("OpenAI 이미지 분석에 실패했습니다.", exception);
		}
	}

	@Override
	public <T> T analyzeImagesStructured(AiMultimodalRequest request, Class<T> responseType) {
		try {
			ResponseInputItem input = imageInput(request);
			StructuredResponseCreateParams<T> params = ResponseCreateParams.builder()
					.model(properties.getModel())
					.inputOfResponse(List.of(input))
					.instructions(request.systemPrompt())
					.text(responseType)
					.build();
			StructuredResponse<T> response = client.responses().create(params);
			return response.output().stream()
					.flatMap(item -> item.message().stream())
					.flatMap(message -> message.content().stream())
					.flatMap(content -> content.outputText().stream())
					.findFirst()
					.orElseThrow(() -> new AiClientException("OpenAI 이미지 구조화 응답에 출력이 없습니다."));
		} catch (AiClientException exception) {
			throw exception;
		} catch (Exception exception) {
			throw translate("OpenAI 이미지 구조화 응답 생성에 실패했습니다.", exception);
		}
	}

	private ResponseInputItem imageInput(AiMultimodalRequest request) throws IOException {
		ResponseInputItem.Message.Builder message = ResponseInputItem.Message.builder()
				.role(ResponseInputItem.Message.Role.USER)
				.addInputTextContent(request.userPrompt());
		for (Path imagePath : request.imagePaths()) {
			message.addContent(toImage(imagePath, request.imageDetail()));
		}
		return ResponseInputItem.ofMessage(message.build());
	}

	private ResponseCreateParams.Builder baseBuilder(AiTextRequest request) {
		ResponseCreateParams.Builder builder = ResponseCreateParams.builder()
				.model(properties.getModel())
				.input(request.userPrompt());
		if (!request.systemPrompt().isBlank()) {
			builder.instructions(request.systemPrompt());
		}
		return builder;
	}

	private ResponseInputImage toImage(Path imagePath, AiMultimodalRequest.ImageDetail detail) throws IOException {
		Path realPath = imagePath.toRealPath();
		if (!Files.isRegularFile(realPath)) {
			throw new AiClientException("이미지 파일이 아닙니다: " + imagePath);
		}
		String mimeType = detectMimeType(realPath);
		String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(realPath));
		return ResponseInputImage.builder()
				.detail(toOpenAiDetail(detail))
				.imageUrl(dataUrl)
				.build();
	}

	private String detectMimeType(Path path) throws IOException {
		String detected = Files.probeContentType(path);
		if (detected != null && detected.startsWith("image/")) {
			return detected;
		}
		String name = path.getFileName().toString().toLowerCase();
		if (name.endsWith(".png")) return "image/png";
		if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
		if (name.endsWith(".webp")) return "image/webp";
		throw new AiClientException("지원하지 않는 이미지 형식입니다: " + path.getFileName());
	}

	private ResponseInputImage.Detail toOpenAiDetail(AiMultimodalRequest.ImageDetail detail) {
		return switch (detail) {
			case LOW -> ResponseInputImage.Detail.LOW;
			case HIGH -> ResponseInputImage.Detail.HIGH;
			case AUTO -> ResponseInputImage.Detail.AUTO;
		};
	}

	private String extractText(Response response) {
		List<String> output = new ArrayList<>();
		response.output().stream()
				.flatMap(item -> item.message().stream())
				.flatMap(message -> message.content().stream())
				.flatMap(content -> content.outputText().stream())
				.map(text -> text.text())
				.forEach(output::add);
		if (output.isEmpty()) {
			throw new AiClientException("OpenAI 응답에 텍스트 출력이 없습니다.");
		}
		return String.join("", output);
	}

	private AiClientException translate(String message, Exception cause) {
		return new AiClientException(message, cause);
	}
}
