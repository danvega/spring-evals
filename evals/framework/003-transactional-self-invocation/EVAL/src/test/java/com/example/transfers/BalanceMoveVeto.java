package com.example.transfers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.h2.api.Trigger;

/**
 * H2 row trigger the hidden test installs on the account table for one
 * transfer. Created as REFUSE_CREDITS it rejects every balance increase,
 * created as REFUSE_DEBITS every decrease. One leg of the transfer then
 * fails inside the database, and whether the other leg survives is
 * observable through the balance endpoint.
 */
public class BalanceMoveVeto implements Trigger {

    private boolean refuseCredits;

    private int balanceColumn;

    @Override
    public void init(Connection connection, String schemaName, String triggerName, String tableName,
            boolean before, int type) throws SQLException {
        refuseCredits = triggerName.equalsIgnoreCase("REFUSE_CREDITS");
        try (ResultSet columns = connection.getMetaData().getColumns(null, schemaName, tableName, "BALANCE")) {
            if (!columns.next()) {
                throw new SQLException("no BALANCE column on " + tableName);
            }
            balanceColumn = columns.getInt("ORDINAL_POSITION") - 1;
        }
    }

    @Override
    public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
        if (oldRow == null || newRow == null) {
            return;
        }
        BigDecimal before = new BigDecimal(String.valueOf(oldRow[balanceColumn]));
        BigDecimal after = new BigDecimal(String.valueOf(newRow[balanceColumn]));
        int direction = after.compareTo(before);
        if (refuseCredits && direction > 0) {
            throw new SQLException("credits are refused while this test runs");
        }
        if (!refuseCredits && direction < 0) {
            throw new SQLException("debits are refused while this test runs");
        }
    }
}
