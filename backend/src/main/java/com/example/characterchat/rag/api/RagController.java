package com.example.characterchat.rag.api;

import com.example.characterchat.rag.application.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/{bookId}/rag")
public class RagController {
	private final RagService service;
	public RagController(RagService service) { this.service = service; }

	@PostMapping("/index")
	public ResponseEntity<RagIndexResponse> index(@PathVariable Long bookId) {
		return ResponseEntity.ok(service.index(bookId));
	}

	@GetMapping("/search")
	public ResponseEntity<RagSearchResponse> search(@PathVariable Long bookId, @RequestParam String query) {
		return ResponseEntity.ok(service.search(bookId, query));
	}
}

