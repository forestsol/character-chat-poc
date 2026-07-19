package com.example.characterchat.book.domain;

public class BookPage {

	private Long id;
	private Long bookId;
	private int pageNumber;

	public BookPage() {
	}

	public BookPage(Long bookId, int pageNumber) {
		this.bookId = bookId;
		this.pageNumber = pageNumber;
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public Long getBookId() { return bookId; }
	public void setBookId(Long bookId) { this.bookId = bookId; }
	public int getPageNumber() { return pageNumber; }
	public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
}
