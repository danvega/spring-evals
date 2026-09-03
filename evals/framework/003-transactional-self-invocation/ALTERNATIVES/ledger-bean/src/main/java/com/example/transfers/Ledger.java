package com.example.transfers;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Single-account operations. Each joins the caller's transaction when there is one. */
@Component
public class Ledger {

    private final AccountRepository accountRepository;

    public Ledger(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public void withdraw(String accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new TransferException("unknown account " + accountId));
        if (account.getBalance().compareTo(amount) < 0) {
            throw new TransferException("insufficient funds in " + accountId);
        }
        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
    }

    /**
     * Deposit owns the rules about which accounts can receive money.
     */
    @Transactional
    public void deposit(String accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new TransferException("unknown account " + accountId));
        if (account.isFrozen()) {
            throw new TransferException("account " + accountId + " is frozen");
        }
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }
}
