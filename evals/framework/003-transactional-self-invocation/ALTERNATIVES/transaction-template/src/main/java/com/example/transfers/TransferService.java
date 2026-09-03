package com.example.transfers;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransferService {

    private final AccountRepository accountRepository;

    private final TransactionTemplate transactionTemplate;

    public TransferService(AccountRepository accountRepository, TransactionTemplate transactionTemplate) {
        this.accountRepository = accountRepository;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Both legs run inside one programmatic transaction, so either both
     * commit or neither does. No proxy is involved, which is why the inner
     * methods can stay plain and be called directly.
     */
    public void transfer(String fromId, String toId, BigDecimal amount) {
        transactionTemplate.executeWithoutResult(status -> {
            withdraw(fromId, amount);
            deposit(toId, amount);
        });
    }

    void withdraw(String accountId, BigDecimal amount) {
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
    void deposit(String accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new TransferException("unknown account " + accountId));
        if (account.isFrozen()) {
            throw new TransferException("account " + accountId + " is frozen");
        }
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }
}
