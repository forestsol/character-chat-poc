package com.example.characterchat.analysis.image.api;

import com.example.characterchat.analysis.image.application.ImageAnalysisService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/books/{bookId}/image-analysis")
public class ImageAnalysisController {
	private final ImageAnalysisService service;

	public ImageAnalysisController(ImageAnalysisService service) { this.service = service; }

	@PostMapping("/analyze")
	public List<ImageFactResponse> analyze(@PathVariable Long bookId) { return service.analyze(bookId); }

	@GetMapping("/facts")
	public List<ImageFactResponse> getFacts(@PathVariable Long bookId) { return service.getFacts(bookId); }
}
