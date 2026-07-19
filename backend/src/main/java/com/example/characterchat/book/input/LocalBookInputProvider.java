package com.example.characterchat.book.input;

import com.example.characterchat.common.exception.InvalidBookInputException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class LocalBookInputProvider implements BookInputProvider {

	private static final Set<String> SUPPORTED_IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

	private final ObjectMapper objectMapper;
	private final Path rootDirectory;

	public LocalBookInputProvider(ObjectMapper objectMapper,
			@Value("${book-input.root-directory:../test-books}") String rootDirectory) {
		this.objectMapper = objectMapper;
		this.rootDirectory = Path.of(rootDirectory).toAbsolutePath().normalize();
	}

	@Override
	public BookInput load(String bookDirectory) {
		if (bookDirectory == null || bookDirectory.isBlank()) {
			throw new InvalidBookInputException("bookDirectory는 필수입니다.");
		}

		try {
			Path realRoot = rootDirectory.toRealPath();
			Path requestedDirectory = realRoot.resolve(bookDirectory).normalize();
			ensureWithinRoot(realRoot, requestedDirectory, "책 디렉터리");
			Path realBookDirectory = requestedDirectory.toRealPath();
			ensureWithinRoot(realRoot, realBookDirectory, "책 디렉터리");
			if (!Files.isDirectory(realBookDirectory)) {
				throw new InvalidBookInputException("책 디렉터리가 아닙니다: " + bookDirectory);
			}

			Path manifestPath = realBookDirectory.resolve("book.json");
			if (!Files.isRegularFile(manifestPath)) {
				throw new InvalidBookInputException("book.json이 없습니다: " + bookDirectory);
			}

			BookManifest manifest = objectMapper.readValue(manifestPath.toFile(), BookManifest.class);
			validateManifest(manifest);
			return toBookInput(realRoot, realBookDirectory, manifest);
		} catch (InvalidBookInputException exception) {
			throw exception;
		} catch (IOException exception) {
			throw new InvalidBookInputException("책 입력 파일을 읽을 수 없습니다: " + exception.getMessage(), exception);
		}
	}

	private BookInput toBookInput(Path realRoot, Path bookDirectory, BookManifest manifest) throws IOException {
		List<PageInput> pages = new ArrayList<>();
		List<PageManifest> sortedPages = manifest.pages().stream()
				.sorted(Comparator.comparingInt(PageManifest::pageNumber))
				.toList();

		for (PageManifest page : sortedPages) {
			Path textPath = resolveRegularFile(bookDirectory, page.textFile(), "텍스트 파일");
			String text = Files.readString(textPath, StandardCharsets.UTF_8);
			List<ImageInput> images = new ArrayList<>();
			for (int index = 0; index < page.imageFiles().size(); index++) {
				Path imagePath = resolveRegularFile(bookDirectory, page.imageFiles().get(index), "이미지 파일");
				validateImageExtension(imagePath);
				String storedPath = realRoot.relativize(imagePath).toString().replace('\\', '/');
				images.add(new ImageInput(index + 1, storedPath));
			}
			pages.add(new PageInput(page.pageNumber(), text, List.copyOf(images)));
		}

		return new BookInput(
				manifest.bookKey().strip(),
				manifest.title().strip(),
				stripToNull(manifest.author()),
				List.copyOf(pages)
		);
	}

	private Path resolveRegularFile(Path bookDirectory, String relativePath, String label) throws IOException {
		if (relativePath == null || relativePath.isBlank()) {
			throw new InvalidBookInputException(label + " 경로가 비어 있습니다.");
		}
		Path resolved = bookDirectory.resolve(relativePath).normalize();
		ensureWithinRoot(bookDirectory, resolved, label);
		Path realPath = resolved.toRealPath();
		ensureWithinRoot(bookDirectory, realPath, label);
		if (!Files.isRegularFile(realPath)) {
			throw new InvalidBookInputException(label + "이 아닙니다: " + relativePath);
		}
		return realPath;
	}

	private void validateManifest(BookManifest manifest) {
		if (manifest == null) {
			throw new InvalidBookInputException("book.json 내용이 비어 있습니다.");
		}
		if (manifest.bookKey() == null || manifest.bookKey().isBlank()) {
			throw new InvalidBookInputException("bookKey는 필수입니다.");
		}
		if (manifest.title() == null || manifest.title().isBlank()) {
			throw new InvalidBookInputException("title은 필수입니다.");
		}
		if (manifest.pages() == null || manifest.pages().isEmpty()) {
			throw new InvalidBookInputException("pages는 한 개 이상이어야 합니다.");
		}

		Set<Integer> pageNumbers = new HashSet<>();
		for (PageManifest page : manifest.pages()) {
			if (page == null || page.pageNumber() < 1) {
				throw new InvalidBookInputException("pageNumber는 1 이상이어야 합니다.");
			}
			if (!pageNumbers.add(page.pageNumber())) {
				throw new InvalidBookInputException("페이지 번호가 중복되었습니다: " + page.pageNumber());
			}
			if (page.textFile() == null || page.textFile().isBlank()) {
				throw new InvalidBookInputException("textFile은 필수입니다. pageNumber=" + page.pageNumber());
			}
			if (page.imageFiles() == null) {
				throw new InvalidBookInputException("imageFiles는 null일 수 없습니다. pageNumber=" + page.pageNumber());
			}
		}
	}

	private void validateImageExtension(Path imagePath) {
		String fileName = imagePath.getFileName().toString();
		int dotIndex = fileName.lastIndexOf('.');
		String extension = dotIndex >= 0 ? fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT) : "";
		if (!SUPPORTED_IMAGE_EXTENSIONS.contains(extension)) {
			throw new InvalidBookInputException("지원하지 않는 이미지 형식입니다: " + fileName);
		}
	}

	private void ensureWithinRoot(Path root, Path candidate, String label) {
		if (!candidate.startsWith(root)) {
			throw new InvalidBookInputException(label + " 경로가 허용된 디렉터리 밖을 가리킵니다.");
		}
	}

	private String stripToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

	private record BookManifest(String bookKey, String title, String author, List<PageManifest> pages) {
	}

	private record PageManifest(int pageNumber, String textFile, List<String> imageFiles) {
	}
}
