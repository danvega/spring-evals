package com.example.transfers;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final Ledger ledger;

    public TransferService(Ledger ledger) {
        this.ledger = ledger;
    }

    /**
     * The transaction boundary is the entry point. Both ledger operations
     * are reached through the ledger bean's proxy and join this
     * transaction, so a failure in either leg rolls back the whole transfer.
     */
    @Transactional
    public void transfer(String fromId, String toId, BigDecimal amount) {
        ledger.withdraw(fromId, amount);
        ledger.deposit(toId, amount);
    }
}
