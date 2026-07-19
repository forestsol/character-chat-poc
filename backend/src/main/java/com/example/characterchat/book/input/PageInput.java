package com.example.characterchat.book.input;

import java.util.List;

public record PageInput(
		int pageNumber,
		String text,
		List<ImageInput> images
) {
}
