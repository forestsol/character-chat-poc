package com.example.characterchat.ai;

import java.nio.file.Path;
import java.util.List;

public record AiMultimodalRequest(
		String systemPrompt,
		String userPrompt,
		List<Path> imagePaths,
		ImageDetail imageDetail
) {

	public AiMultimodalRequest {
		if (userPrompt == null || userPrompt.isBlank()) {
			throw new IllegalArgumentException("userPrompt는 필수입니다.");
		}
		if (imagePaths == null || imagePaths.isEmpty()) {
			throw new IllegalArgumentException("imagePaths는 한 개 이상이어야 합니다.");
		}
		if (imagePaths.stream().anyMatch(path -> path == null)) {
			throw new IllegalArgumentException("imagePaths에 null을 포함할 수 없습니다.");
		}
		systemPrompt = systemPrompt == null ? "" : systemPrompt;
		imagePaths = List.copyOf(imagePaths);
		imageDetail = imageDetail == null ? ImageDetail.AUTO : imageDetail;
	}

	public enum ImageDetail {
		AUTO,
		LOW,
		HIGH
	}
}
