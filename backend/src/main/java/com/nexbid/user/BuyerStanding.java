package com.nexbid.user;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EN: How many lots a person won and never paid for. Counted as each payment expires (the payment module tells this
 *     one); read when they bid on something costly, and shown to sellers beside their orders.
 * VI: Số lô một người đã thắng mà không trả tiền. Đếm mỗi khi một khoản thanh toán hết hạn (module payment báo cho module
 *     này); được đọc khi họ trả giá món đắt tiền, và hiện cho người bán cạnh đơn hàng.
 */
@Service
public class BuyerStanding {

    private final JdbcTemplate jdbc;

    BuyerStanding(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void recordUnpaidWin(UUID userId) {
        jdbc.update("UPDATE users SET unpaid_wins = unpaid_wins + 1 WHERE id = ?", userId);
    }

    public int unpaidWinsOf(UUID userId) {
        Integer count = jdbc.query("SELECT unpaid_wins FROM users WHERE id = ?",
                rs -> rs.next() ? rs.getInt(1) : 0, userId);
        return count == null ? 0 : count;
    }

    public Map<UUID, Integer> unpaidWinsOf(Collection<UUID> userIds) {
        Map<UUID, Integer> counts = new HashMap<>();
        if (userIds.isEmpty()) {
            return counts;
        }
        jdbc.query("SELECT id, unpaid_wins FROM users WHERE id = ANY (?)", rs -> {
            counts.put(rs.getObject(1, UUID.class), rs.getInt(2));
        }, (Object) userIds.toArray(UUID[]::new));
        return counts;
    }
}
