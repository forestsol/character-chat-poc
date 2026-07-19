package com.example.characterchat.analysis.entity.api;

import com.example.characterchat.analysis.entity.application.EntityExtractionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/books/{bookId}/entity-candidates")
public class EntityCandidateController {
	private final EntityExtractionService service;

	public EntityCandidateController(EntityExtractionService service) { this.service = service; }

	@PostMapping("/extract")
	public List<EntityCandidateResponse> extract(@PathVariable Long bookId) { return service.extract(bookId); }

	@GetMapping
	public List<EntityCandidateResponse> getCandidates(@PathVariable Long bookId) { return service.getCandidates(bookId); }
}
