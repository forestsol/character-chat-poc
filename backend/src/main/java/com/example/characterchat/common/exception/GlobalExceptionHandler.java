package com.example.characterchat.common.exception;

import com.example.characterchat.analysis.entity.application.EntityExtractionException;
import com.example.characterchat.analysis.image.application.ImageAnalysisException;
import com.example.characterchat.analysis.kg.application.KnowledgeGraphException;
import com.example.characterchat.review.application.CharacterReviewException;
import com.example.characterchat.review.application.RoleRecommendationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler({InvalidBookInputException.class, HttpMessageNotReadableException.class})
	public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception exception, HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
	}

	@ExceptionHandler(BookNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(BookNotFoundException exception, HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, exception.getMessage(), request);
	}

	@ExceptionHandler(DuplicateBookException.class)
	public ResponseEntity<ApiErrorResponse> handleConflict(DuplicateBookException exception, HttpServletRequest request) {
		return response(HttpStatus.CONFLICT, exception.getMessage(), request);
	}

	@ExceptionHandler(EntityExtractionException.class)
	public ResponseEntity<ApiErrorResponse> handleAiAnalysis(EntityExtractionException exception, HttpServletRequest request) {
		return response(HttpStatus.BAD_GATEWAY, exception.getMessage(), request);
	}

	@ExceptionHandler(ImageAnalysisException.class)
	public ResponseEntity<ApiErrorResponse> handleImageAnalysis(ImageAnalysisException exception, HttpServletRequest request) {
		return response(HttpStatus.BAD_GATEWAY, exception.getMessage(), request);
	}

	@ExceptionHandler(KnowledgeGraphException.class)
	public ResponseEntity<ApiErrorResponse> handleKnowledgeGraph(KnowledgeGraphException exception, HttpServletRequest request) {
		return response(HttpStatus.BAD_GATEWAY, exception.getMessage(), request);
	}

	@ExceptionHandler(CharacterReviewException.class)
	public ResponseEntity<ApiErrorResponse> handleCharacterReview(CharacterReviewException exception, HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
	}

	@ExceptionHandler(RoleRecommendationException.class)
	public ResponseEntity<ApiErrorResponse> handleRoleRecommendation(RoleRecommendationException exception, HttpServletRequest request) {
		return response(HttpStatus.BAD_GATEWAY, exception.getMessage(), request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
		log.error("예상하지 못한 서버 오류", exception);
		return response(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.", request);
	}

	private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String message, HttpServletRequest request) {
		return ResponseEntity.status(status).body(new ApiErrorResponse(
				OffsetDateTime.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()
		));
	}
}
