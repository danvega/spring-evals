package com.example.fieldnotes;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface ProspectRepository extends ListCrudRepository<Prospect, Long> {

    List<Prospect> findByEmailLike(String pattern);
}
