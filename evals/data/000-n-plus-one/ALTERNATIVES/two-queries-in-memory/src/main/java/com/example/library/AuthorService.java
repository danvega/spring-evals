package com.example.library;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorService {

    private final AuthorRepository authorRepository;
    private final BookRepository bookRepository;

    public AuthorService(AuthorRepository authorRepository, BookRepository bookRepository) {
        this.authorRepository = authorRepository;
        this.bookRepository = bookRepository;
    }

    @Transactional(readOnly = true)
    public List<AuthorSummary> findAllWithBooks() {
        List<Author> authors = authorRepository.findAll();
        Map<Long, List<String>> titlesByAuthor = new HashMap<>();
        for (Book book : bookRepository.findAll()) {
            titlesByAuthor.computeIfAbsent(book.getAuthor().getId(), id -> new ArrayList<>())
                    .add(book.getTitle());
        }
        return authors.stream()
                .map(author -> new AuthorSummary(
                        author.getName(),
                        titlesByAuthor.getOrDefault(author.getId(), List.of())))
                .toList();
    }
}
