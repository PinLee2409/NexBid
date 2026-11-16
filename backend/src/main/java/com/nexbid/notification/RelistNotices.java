package com.nexbid.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.nexbid.auction.AuctionRelistedEvent;
import com.nexbid.auction.AuctionService;

/**
 * EN: Everyone watching a lot that relisted itself keeps watching the new one, and hears its new, lower start: the
 *     thing a hesitant bidder was waiting for. A relist at the same price moves the watch quietly.
 * VI: Mọi người đang theo dõi một lô vừa tự đăng lại sẽ tiếp tục theo dõi lô mới, và được báo giá khởi điểm mới thấp
 *     hơn: đúng điều người còn đắn đo đang chờ. Đăng lại cùng giá thì chỉ chuyển theo dõi, không báo.
 */
@Component
class RelistNotices {

    private final JdbcTemplate jdbc;
    private final NotificationService notifications;
    private final AuctionService auctions;

    RelistNotices(JdbcTemplate jdbc, NotificationService notifications, AuctionService auctions) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.auctions = auctions;
    }

    @EventListener
    void onRelisted(AuctionRelistedEvent event) {
        List<UUID> watchers = jdbc.queryForList("SELECT user_id FROM watchlists WHERE auction_id = ?", UUID.class,
                event.oldAuctionId());
        Instant now = Instant.now();
        boolean cheaper = event.newStart().compareTo(event.oldStart()) < 0;
        String title = auctions.lotTitleOf(event.newAuctionId());
        for (UUID watcher : watchers) {
            jdbc.update("""
                    INSERT INTO watchlists (id, user_id, auction_id, created_at) VALUES (?, ?, ?, ?)
                    ON CONFLICT (user_id, auction_id) DO NOTHING
                    """, UUID.randomUUID(), watcher, event.newAuctionId(), now.atOffset(ZoneOffset.UTC));
            if (cheaper) {
                notifications.notify(watcher, NotificationType.LOT_RELISTED, "Listed again, lower",
                        clip(title + ": back on sale from " + vnd(event.newStart()) + " (was " + vnd(event.oldStart())
                                + ")."), event.newAuctionId(), now);
            }
        }
    }

    private static String vnd(BigDecimal amount) {
        return String.format("%,d VND", amount.longValue());
    }

    private static String clip(String text) {
        return text.length() <= 500 ? text : text.substring(0, 497) + "...";
    }
}
