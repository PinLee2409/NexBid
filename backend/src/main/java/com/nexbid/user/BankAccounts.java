package com.nexbid.user;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EN: Each account's bank account, one per user (table bank_accounts). Payouts and refunds read it; only the owner
 *     changes it, and every change is announced so the audit log keeps a trail.
 * VI: Tài khoản ngân hàng của mỗi người dùng, mỗi người một tài khoản (bảng bank_accounts). Chi trả và hoàn tiền đọc
 *     nó; chỉ chủ tài khoản được đổi, và mỗi lần đổi đều được báo để audit log giữ dấu vết.
 */
@Service
public class BankAccounts {

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    BankAccounts(JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    public Optional<BankAccount> of(UUID userId) {
        return jdbc.query("SELECT bank_name, account_number, account_holder FROM bank_accounts WHERE user_id = ?",
                (rs, n) -> new BankAccount(rs.getString("bank_name"), rs.getString("account_number"),
                        rs.getString("account_holder")),
                userId).stream().findFirst();
    }

    @Transactional
    public BankAccount save(UUID userId, BankAccount account) {
        BankAccount clean = account.cleaned();
        String before = of(userId).map(BankAccount::masked).orElse(null);
        jdbc.update("""
                INSERT INTO bank_accounts (user_id, bank_name, account_number, account_holder, updated_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (user_id) DO UPDATE SET bank_name = EXCLUDED.bank_name,
                    account_number = EXCLUDED.account_number, account_holder = EXCLUDED.account_holder,
                    updated_at = now()
                """, userId, clean.bankName(), clean.accountNumber(), clean.accountHolder());
        events.publishEvent(new BankAccountSavedEvent(userId, before, clean.masked(), Instant.now()));
        return clean;
    }
}
