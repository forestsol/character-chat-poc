package com.example.characterchat.review.api;

import com.example.characterchat.review.application.CharacterReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/books/{bookId}/character-reviews")
public class CharacterReviewController {
	private final CharacterReviewService service;
	public CharacterReviewController(CharacterReviewService service) { this.service = service; }
	@GetMapping public List<CharacterReviewResponse> get(@PathVariable Long bookId) { return service.getReviews(bookId); }
	@PostMapping("/recommend") public List<CharacterReviewResponse> recommend(@PathVariable Long bookId) { return service.recommend(bookId); }
	@PutMapping("/{candidateId}") public List<CharacterReviewResponse> review(@PathVariable Long bookId,
			@PathVariable Long candidateId, @RequestBody CharacterReviewRequest request) { return service.review(bookId, candidateId, request); }
}
