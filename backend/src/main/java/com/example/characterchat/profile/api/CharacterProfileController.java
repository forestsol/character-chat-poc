package com.example.characterchat.profile.api;

import com.example.characterchat.profile.application.CharacterProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/{bookId}/character-profile")
public class CharacterProfileController {
	private final CharacterProfileService service;
	public CharacterProfileController(CharacterProfileService service) { this.service=service; }
	@PostMapping("/generate") public CharacterProfileResponse generate(@PathVariable Long bookId) { return service.generate(bookId); }
	@GetMapping public CharacterProfileResponse get(@PathVariable Long bookId) { return service.get(bookId); }
}
