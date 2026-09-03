package com.example.transfers;

import java.math.BigDecimal;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Seeds the demo ledger. DO NOT MODIFY. */
@Component
public class AccountSeeder implements CommandLineRunner {

    private final AccountRepository accountRepository;

    public AccountSeeder(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public void run(String... args) {
        if (accountRepository.count() > 0) {
            return;
        }
        accountRepository.save(new Account("alice", "Alice Chen", new BigDecimal("1000.00"), false));
        accountRepository.save(new Account("bob", "Bob Osei", new BigDecimal("500.00"), false));
        accountRepository.save(new Account("carol", "Carol Diaz", new BigDecimal("250.00"), true));
    }
}
