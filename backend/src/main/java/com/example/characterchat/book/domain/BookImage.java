package com.example.characterchat.book.domain;

public record BookImage(
		Long id,
		Long bookId,
		Long pageId,
		int imageOrder,
		String filePath
) {
}
