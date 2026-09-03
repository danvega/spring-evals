package com.example.expenses;

import org.springframework.data.repository.ListCrudRepository;

public interface ExpenseRepository extends ListCrudRepository<Expense, Long> {
}
