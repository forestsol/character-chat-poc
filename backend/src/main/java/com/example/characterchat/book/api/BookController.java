package com.example.characterchat.book.api;

import com.example.characterchat.book.application.BookService;
import com.example.characterchat.common.exception.InvalidBookInputException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/books")
public class BookController {

	private final BookService bookService;

	public BookController(BookService bookService) {
		this.bookService = bookService;
	}

	@PostMapping("/import")
	@ResponseStatus(HttpStatus.CREATED)
	public BookResponse importBook(@RequestBody BookImportRequest request) {
		if (request == null) {
			throw new InvalidBookInputException("요청 본문은 필수입니다.");
		}
		return bookService.importBook(request.bookDirectory());
	}

	@GetMapping
	public List<BookResponse.Summary> getBooks() {
		return bookService.getBooks();
	}

	@GetMapping("/{bookId}")
	public BookResponse getBook(@PathVariable Long bookId) {
		return bookService.getBook(bookId);
	}
}
