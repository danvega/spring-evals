package com.example.library;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Seeds a small slice of the production dataset: 30 authors with 10 books
 * each. Production has tens of thousands. DO NOT MODIFY.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;

    public DataSeeder(AuthorRepository authorRepository, BookRepository bookRepository) {
        this.authorRepository = authorRepository;
        this.bookRepository = bookRepository;
    }

    @Override
    public void run(String... args) {
        if (authorRepository.count() > 0) {
            return;
        }
        for (int a = 1; a <= 30; a++) {
            Author author = authorRepository.save(new Author("Author " + a));
            List<Book> books = new ArrayList<>();
            for (int b = 1; b <= 10; b++) {
                books.add(new Book("Book " + b + " by Author " + a, author));
            }
            bookRepository.saveAll(books);
        }
    }
}
