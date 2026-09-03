package com.example.transfers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;

/**
 * Atomic, but it reaches past Spring's transaction management and rewrites
 * both ledger operations in SQL on a hand-managed connection.
 */
@Service
public class TransferService {

    private final DataSource dataSource;

    public TransferService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void transfer(String fromId, String toId, BigDecimal amount) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                BigDecimal fromBalance = balanceOf(connection, fromId);
                if (fromBalance.compareTo(amount) < 0) {
                    throw new TransferException("insufficient funds in " + fromId);
                }
                BigDecimal toBalance = balanceOf(connection, toId);
                if (frozen(connection, toId)) {
                    throw new TransferException("account " + toId + " is frozen");
                }
                updateBalance(connection, fromId, fromBalance.subtract(amount));
                updateBalance(connection, toId, toBalance.add(amount));
                connection.commit();
            }
            catch (SQLException | RuntimeException failure) {
                connection.rollback();
                if (failure instanceof RuntimeException runtimeFailure) {
                    throw runtimeFailure;
                }
                throw new TransferException("transfer failed: " + failure.getMessage());
            }
        }
        catch (SQLException failure) {
            throw new TransferException("transfer failed: " + failure.getMessage());
        }
    }

    private BigDecimal balanceOf(Connection connection, String accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select balance from account where id = ?")) {
            statement.setString(1, accountId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new TransferException("unknown account " + accountId);
                }
                return rows.getBigDecimal(1);
            }
        }
    }

    private boolean frozen(Connection connection, String accountId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select frozen from account where id = ?")) {
            statement.setString(1, accountId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new TransferException("unknown account " + accountId);
                }
                return rows.getBoolean(1);
            }
        }
    }

    private void updateBalance(Connection connection, String accountId, BigDecimal balance) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "update account set balance = ? where id = ?")) {
            statement.setBigDecimal(1, balance);
            statement.setString(2, accountId);
            statement.executeUpdate();
        }
    }
}
