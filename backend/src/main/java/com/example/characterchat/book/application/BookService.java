package com.example.characterchat.book.application;

import com.example.characterchat.book.api.BookResponse;
import com.example.characterchat.book.domain.Book;
import com.example.characterchat.book.domain.BookImage;
import com.example.characterchat.book.domain.BookPage;
import com.example.characterchat.book.domain.BookParagraph;
import com.example.characterchat.book.input.BookInput;
import com.example.characterchat.book.input.BookInputProvider;
import com.example.characterchat.book.input.PageInput;
import com.example.characterchat.book.persistence.BookMapper;
import com.example.characterchat.common.exception.BookNotFoundException;
import com.example.characterchat.common.exception.DuplicateBookException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class BookService {

	private final BookInputProvider bookInputProvider;
	private final BookMapper bookMapper;

	public BookService(BookInputProvider bookInputProvider, BookMapper bookMapper) {
		this.bookInputProvider = bookInputProvider;
		this.bookMapper = bookMapper;
	}

	@Transactional
	public BookResponse importBook(String bookDirectory) {
		BookInput input = bookInputProvider.load(bookDirectory);
		if (bookMapper.existsByBookKey(input.bookKey())) {
			throw new DuplicateBookException(input.bookKey());
		}

		try {
			Book book = new Book(input.bookKey(), input.title(), input.author(), "IMPORTED");
			bookMapper.insertBook(book);
			int sourceOrder = 1;

			for (PageInput inputPage : input.pages()) {
				BookPage page = new BookPage(book.getId(), inputPage.pageNumber());
				bookMapper.insertPage(page);

				List<String> paragraphs = splitParagraphs(inputPage.text());
				for (int index = 0; index < paragraphs.size(); index++) {
					bookMapper.insertParagraph(new BookParagraph(
							null, book.getId(), page.getId(), index + 1, sourceOrder++, paragraphs.get(index)
					));
				}

				inputPage.images().forEach(image -> bookMapper.insertImage(new BookImage(
						null, book.getId(), page.getId(), image.imageOrder(), image.filePath()
				)));
			}
			return getBook(book.getId());
		} catch (DuplicateKeyException exception) {
			throw new DuplicateBookException(input.bookKey(), exception);
		}
	}

	@Transactional(readOnly = true)
	public List<BookResponse.Summary> getBooks() {
		return bookMapper.findAllBooks().stream().map(BookResponse.Summary::from).toList();
	}

	@Transactional(readOnly = true)
	public BookResponse getBook(Long id) {
		Book book = bookMapper.findBookById(id);
		if (book == null) {
			throw new BookNotFoundException(id);
		}
		return BookResponse.from(
				book,
				bookMapper.findPagesByBookId(id),
				bookMapper.findParagraphsByBookId(id),
				bookMapper.findImagesByBookId(id)
		);
	}

	private List<String> splitParagraphs(String text) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		return Arrays.stream(text.split("(?:\\R\\s*){2,}"))
				.map(String::strip)
				.filter(paragraph -> !paragraph.isEmpty())
				.toList();
	}
}
