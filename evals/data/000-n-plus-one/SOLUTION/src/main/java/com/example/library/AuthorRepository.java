package com.example.library;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    @Query("select distinct a from Author a left join fetch a.books order by a.id")
    List<Author> findAllWithBooks();
}
