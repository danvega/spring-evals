package com.example.transfers;

import java.math.BigDecimal;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final AccountRepository accountRepository;

    private final TransferService self;

    public TransferService(AccountRepository accountRepository, @Lazy TransferService self) {
        this.accountRepository = accountRepository;
        this.self = self;
    }

    /**
     * transfer is the transaction boundary. withdraw and deposit are called
     * through the proxy held in self, never through this, so their own
     * transaction attributes apply and they join the transfer's transaction.
     */
    @Transactional
    public void transfer(String fromId, String toId, BigDecimal amount) {
        self.withdraw(fromId, amount);
        self.deposit(toId, amount);
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
