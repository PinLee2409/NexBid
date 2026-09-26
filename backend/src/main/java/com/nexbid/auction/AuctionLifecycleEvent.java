package com.nexbid.auction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.modulith.events.Externalized;

import com.nexbid.auction.entity.Auction;

/**
 * EN: A lot opened, closed or had its close pushed back (guide §25, §26, §30). Raised inside the
 *     transaction, delivered only after it commits.
 * VI: Một lô vừa mở, vừa đóng hoặc bị lùi giờ đóng (guide §25, §26, §30). Phát ra trong transaction, chỉ
 *     giao đi sau khi transaction commit.
 */
@Externalized("nexbid.auctions::#{auctionId()}")
public record AuctionLifecycleEvent(
        String type,
        UUID auctionId,
        AuctionStatus status,
        Instant startTime,
        Instant endTime,
        BigDecimal currentPrice,
        // EN: For listeners inside the server only; the public broadcast never copies these two.
        // VI: Chỉ dành cho listener bên trong server; bản tin công khai không bao giờ chép hai trường này.
        UUID winnerId,
        // EN: Who leads right now — on an extension, the bidder whose last-minute bid caused it.
        // VI: Ai đang dẫn đầu lúc này — khi gia hạn, đó là người có lượt trả giá phút chót gây ra nó.
        UUID leaderId) {

    public static final String STARTED = "AUCTION_STARTED";
    public static final String ENDED = "AUCTION_ENDED";
    public static final String EXTENDED = "AUCTION_EXTENDED";

    static AuctionLifecycleEvent started(Auction auction) {
        return of(STARTED, auction);
    }

    static AuctionLifecycleEvent ended(Auction auction) {
        return of(ENDED, auction);
    }

    static AuctionLifecycleEvent extended(Auction auction) {
        return of(EXTENDED, auction);
    }

    private static AuctionLifecycleEvent of(String type, Auction auction) {
        return new AuctionLifecycleEvent(
                type, auction.getId(), auction.getStatus(),
                auction.getStartTime(), auction.getEndTime(),
                auction.getCurrentPrice(), auction.getWinnerId(), auction.getLeadingBidderId());
    }
}
