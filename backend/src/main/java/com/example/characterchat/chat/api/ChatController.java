package com.example.characterchat.chat.api;

import com.example.characterchat.chat.application.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/{bookId}/chat")
public class ChatController {
	private final ChatService service;
	public ChatController(ChatService service) { this.service = service; }

	@PostMapping
	public ResponseEntity<ChatResponse> chat(@PathVariable Long bookId, @RequestBody ChatRequest request) {
		return ResponseEntity.ok(service.chat(bookId, request.question(), request.history()));
	}
}
