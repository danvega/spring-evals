package com.example.fieldnotes;

import java.util.List;

import org.springframework.data.repository.ListCrudRepository;

public interface CustomerRepository extends ListCrudRepository<Customer, Long> {

    List<Customer> findByActiveTrue();

    List<Customer> findByLastNameIgnoreCase(String lastName);
}
