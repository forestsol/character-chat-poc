package com.example.characterchat.common.exception;

public class DuplicateBookException extends RuntimeException {

	public DuplicateBookException(String bookKey) {
		super("이미 저장된 bookKey입니다: " + bookKey);
	}

	public DuplicateBookException(String bookKey, Throwable cause) {
		super("이미 저장된 bookKey입니다: " + bookKey, cause);
	}
}
