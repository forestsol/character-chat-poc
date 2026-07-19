package com.example.characterchat.book.input;

import java.util.List;

public record BookInput(
		String bookKey,
		String title,
		String author,
		List<PageInput> pages
) {
}
