package com.example.bookshelf.book;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookController {

    private final BookRepository books;

    BookController(BookRepository books) {
        this.books = books;
    }

    @GetMapping("/api/books")
    List<Book> findAll() {
        return books.findAll();
    }
}
