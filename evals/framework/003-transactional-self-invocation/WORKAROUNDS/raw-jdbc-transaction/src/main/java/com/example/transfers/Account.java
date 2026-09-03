package com.example.transfers;

import java.math.BigDecimal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Ledger row shared with other services. DO NOT MODIFY. */
@Entity
public class Account {

    @Id
    private String id;

    private String owner;

    private BigDecimal balance;

    private boolean frozen;

    protected Account() {
    }

    public Account(String id, String owner, BigDecimal balance, boolean frozen) {
        this.id = id;
        this.owner = owner;
        this.balance = balance;
        this.frozen = frozen;
    }

    public String getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public boolean isFrozen() {
        return frozen;
    }
}
