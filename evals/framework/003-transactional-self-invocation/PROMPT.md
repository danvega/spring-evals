# Money disappears when a transfer fails

Our nightly reconciliation keeps finding missing funds in this ledger service. The pattern: someone transfers money to an account that cannot receive it (for example a frozen account), the transfer correctly fails, but the money still leaves the source account. It is debited and never credited anywhere.

A transfer must be atomic. Either both the withdrawal and the deposit happen, or neither does. Any failure at any point during a transfer must leave every balance exactly as it was.

To reproduce: `POST /api/transfers` with `{"from": "alice", "to": "carol", "amount": 100}` fails as expected because carol's account is frozen, but alice's balance drops by 100 anyway.

Constraints:

- Do not add dependencies
- `deposit` owns the rules about which accounts can receive money. Do not duplicate those checks elsewhere or pre-validate the target before withdrawing; the fix reviewers will accept is making the whole transfer atomic
- Do not modify `AccountSeeder`, and keep the endpoints and response shapes unchanged

You are done when a failed transfer leaves all balances untouched and successful transfers still move money.
