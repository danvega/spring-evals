package com.example.transfers;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final AccountRepository accountRepository;

    public TransferService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public void transfer(String fromId, String toId, BigDecimal amount) {
        withdraw(fromId, amount);
        deposit(toId, amount);
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
