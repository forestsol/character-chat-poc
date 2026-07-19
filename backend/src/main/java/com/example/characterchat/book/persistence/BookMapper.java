package com.example.characterchat.book.persistence;

import com.example.characterchat.book.domain.Book;
import com.example.characterchat.book.domain.BookImage;
import com.example.characterchat.book.domain.BookPage;
import com.example.characterchat.book.domain.BookParagraph;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookMapper {

	boolean existsByBookKey(String bookKey);

	void insertBook(Book book);

	void insertPage(BookPage page);

	void insertParagraph(BookParagraph paragraph);

	void insertImage(BookImage image);

	List<Book> findAllBooks();

	Book findBookById(Long id);

	List<BookPage> findPagesByBookId(Long bookId);

	List<BookParagraph> findParagraphsByBookId(Long bookId);

	List<BookImage> findImagesByBookId(Long bookId);

	void deleteBookByBookKey(@Param("bookKey") String bookKey);
}
