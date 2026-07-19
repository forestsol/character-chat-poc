package com.example.characterchat.book.api;

import com.example.characterchat.book.domain.Book;
import com.example.characterchat.book.domain.BookImage;
import com.example.characterchat.book.domain.BookPage;
import com.example.characterchat.book.domain.BookParagraph;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record BookResponse(
		Long id,
		String bookKey,
		String title,
		String author,
		String status,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt,
		List<Page> pages
) {

	public static BookResponse from(Book book, List<BookPage> pages,
			List<BookParagraph> paragraphs, List<BookImage> images) {
		Map<Long, List<BookParagraph>> paragraphsByPage = paragraphs.stream()
				.collect(Collectors.groupingBy(BookParagraph::pageId));
		Map<Long, List<BookImage>> imagesByPage = images.stream()
				.collect(Collectors.groupingBy(BookImage::pageId));

		List<Page> pageResponses = pages.stream()
				.map(page -> new Page(
						page.getId(),
						page.getPageNumber(),
						paragraphsByPage.getOrDefault(page.getId(), List.of()).stream()
								.map(Paragraph::from)
								.toList(),
						imagesByPage.getOrDefault(page.getId(), List.of()).stream()
								.map(Image::from)
								.toList()
				))
				.toList();

		return new BookResponse(
				book.getId(), book.getBookKey(), book.getTitle(), book.getAuthor(), book.getStatus(),
				book.getCreatedAt(), book.getUpdatedAt(), pageResponses
		);
	}

	public record Summary(
			Long id,
			String bookKey,
			String title,
			String author,
			String status,
			OffsetDateTime createdAt,
			OffsetDateTime updatedAt
	) {
		public static Summary from(Book book) {
			return new Summary(book.getId(), book.getBookKey(), book.getTitle(), book.getAuthor(),
					book.getStatus(), book.getCreatedAt(), book.getUpdatedAt());
		}
	}

	public record Page(Long id, int pageNumber, List<Paragraph> paragraphs, List<Image> images) {
	}

	public record Paragraph(Long id, int paragraphIndex, int sourceOrder, String content) {
		private static Paragraph from(BookParagraph paragraph) {
			return new Paragraph(paragraph.id(), paragraph.paragraphIndex(), paragraph.sourceOrder(), paragraph.content());
		}
	}

	public record Image(Long id, int imageOrder, String filePath) {
		private static Image from(BookImage image) {
			return new Image(image.id(), image.imageOrder(), image.filePath());
		}
	}
}
