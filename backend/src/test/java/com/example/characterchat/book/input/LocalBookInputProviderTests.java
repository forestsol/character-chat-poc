package com.example.characterchat.book.input;

import com.example.characterchat.common.exception.InvalidBookInputException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalBookInputProviderTests {

	@TempDir
	Path rootDirectory;

	@Test
	void loadsAndSortsValidBookInput() throws Exception {
		Path bookDirectory = Files.createDirectories(rootDirectory.resolve("sample"));
		Files.createDirectories(bookDirectory.resolve("pages"));
		Files.createDirectories(bookDirectory.resolve("images"));
		Files.writeString(bookDirectory.resolve("pages/page-001.txt"), "첫 문단\n\n두 번째 문단", StandardCharsets.UTF_8);
		Files.write(bookDirectory.resolve("images/page-001-01.png"), new byte[]{1, 2, 3});
		Files.writeString(bookDirectory.resolve("book.json"), """
				{
				  "bookKey": "sample",
				  "title": "샘플 책",
				  "author": "테스트",
				  "pages": [{
				    "pageNumber": 1,
				    "textFile": "pages/page-001.txt",
				    "imageFiles": ["images/page-001-01.png"]
				  }]
				}
				""", StandardCharsets.UTF_8);

		BookInput input = provider().load("sample");

		assertThat(input.bookKey()).isEqualTo("sample");
		assertThat(input.pages()).hasSize(1);
		assertThat(input.pages().get(0).text()).contains("첫 문단", "두 번째 문단");
		assertThat(input.pages().get(0).images().get(0).filePath()).isEqualTo("sample/images/page-001-01.png");
	}

	@Test
	void rejectsDuplicatePageNumbers() throws Exception {
		Path bookDirectory = Files.createDirectories(rootDirectory.resolve("duplicate"));
		Files.writeString(bookDirectory.resolve("book.json"), """
				{
				  "bookKey": "duplicate",
				  "title": "중복",
				  "pages": [
				    {"pageNumber": 1, "textFile": "a.txt", "imageFiles": []},
				    {"pageNumber": 1, "textFile": "b.txt", "imageFiles": []}
				  ]
				}
				""", StandardCharsets.UTF_8);

		assertThatThrownBy(() -> provider().load("duplicate"))
				.isInstanceOf(InvalidBookInputException.class)
				.hasMessageContaining("중복");
	}

	@Test
	void rejectsPathOutsideBookDirectory() throws Exception {
		Files.writeString(rootDirectory.resolve("outside.txt"), "외부", StandardCharsets.UTF_8);
		Path bookDirectory = Files.createDirectories(rootDirectory.resolve("escape"));
		Files.writeString(bookDirectory.resolve("book.json"), """
				{
				  "bookKey": "escape",
				  "title": "경로 이탈",
				  "pages": [{"pageNumber": 1, "textFile": "../outside.txt", "imageFiles": []}]
				}
				""", StandardCharsets.UTF_8);

		assertThatThrownBy(() -> provider().load("escape"))
				.isInstanceOf(InvalidBookInputException.class)
				.hasMessageContaining("밖");
	}

	@Test
	void rejectsMissingReferencedFile() throws Exception {
		Path bookDirectory = Files.createDirectories(rootDirectory.resolve("missing"));
		Files.writeString(bookDirectory.resolve("book.json"), """
				{
				  "bookKey": "missing",
				  "title": "누락",
				  "pages": [{"pageNumber": 1, "textFile": "missing.txt", "imageFiles": []}]
				}
				""", StandardCharsets.UTF_8);

		assertThatThrownBy(() -> provider().load("missing"))
				.isInstanceOf(InvalidBookInputException.class)
				.hasMessageContaining("읽을 수 없습니다");
	}

	private LocalBookInputProvider provider() {
		return new LocalBookInputProvider(new ObjectMapper(), rootDirectory.toString());
	}
}
