package com.example.characterchat.analysis.kg.api;

import com.example.characterchat.analysis.kg.application.KnowledgeGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/{bookId}/kg")
public class KnowledgeGraphController {
	private final KnowledgeGraphService service;
	public KnowledgeGraphController(KnowledgeGraphService service) { this.service = service; }
	@PostMapping("/build") public KnowledgeGraphResponse build(@PathVariable Long bookId) { return service.build(bookId); }
	@GetMapping public KnowledgeGraphResponse get(@PathVariable Long bookId) { return service.get(bookId); }
}
