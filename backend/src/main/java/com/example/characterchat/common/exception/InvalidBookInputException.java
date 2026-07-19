package com.example.characterchat.common.exception;

public class InvalidBookInputException extends RuntimeException {

	public InvalidBookInputException(String message) {
		super(message);
	}

	public InvalidBookInputException(String message, Throwable cause) {
		super(message, cause);
	}
}
