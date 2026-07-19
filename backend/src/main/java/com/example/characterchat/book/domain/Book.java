package com.example.characterchat.book.domain;

import java.time.OffsetDateTime;

public class Book {

	private Long id;
	private String bookKey;
	private String title;
	private String author;
	private String status;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;

	public Book() {
	}

	public Book(String bookKey, String title, String author, String status) {
		this.bookKey = bookKey;
		this.title = title;
		this.author = author;
		this.status = status;
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }
	public String getBookKey() { return bookKey; }
	public void setBookKey(String bookKey) { this.bookKey = bookKey; }
	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }
	public String getAuthor() { return author; }
	public void setAuthor(String author) { this.author = author; }
	public String getStatus() { return status; }
	public void setStatus(String status) { this.status = status; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
