package com.example.library;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorService {

    private final AuthorRepository authorRepository;

    public AuthorService(AuthorRepository authorRepository) {
        this.authorRepository = authorRepository;
    }

    @Transactional(readOnly = true)
    public List<AuthorSummary> findAllWithBooks() {
        return authorRepository.findAllWithBooks().stream()
                .map(author -> new AuthorSummary(
                        author.getName(),
                        author.getBooks().stream().map(Book::getTitle).toList()))
                .toList();
    }
}
