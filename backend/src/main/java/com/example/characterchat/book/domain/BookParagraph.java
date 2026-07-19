package com.example.characterchat.book.domain;

public record BookParagraph(
		Long id,
		Long bookId,
		Long pageId,
		int paragraphIndex,
		int sourceOrder,
		String content
) {
}
