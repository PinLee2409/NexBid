package com.nexbid.bid;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.BiddingState;
import com.nexbid.bid.entity.Bid;
import com.nexbid.bid.repository.AutoBidRepository;
import com.nexbid.bid.repository.BidRepository;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;

/**
 * EN: Taking a bid back. Allowed for two reasons only: within a few minutes of bidding (QUICK), or when the last bid is
 *     plainly a slip of the keyboard — at least {@code typoFactor} times the price it was placed against (TYPO). Never in
 *     a lot's last minutes, and at most {@code monthlyLimit} times in 30 days. A retraction takes back all of the
 *     bidder's bids on that lot and turns off their auto bid there, then the lot goes back to what the other bids say.
 *     The bids are deleted; {@code bid_retractions} keeps the record, which staff can read.
 * VI: Rút lại lượt trả giá. Chỉ được vì hai lý do: trong vài phút sau khi trả (QUICK), hoặc khi lượt cuối rõ ràng là gõ
 *     nhầm — ít nhất {@code typoFactor} lần mức giá lúc nó được đặt (TYPO). Không bao giờ trong những phút cuối của lô,
 *     và tối đa {@code monthlyLimit} lần trong 30 ngày. Một lần rút lấy lại mọi lượt của người đó trên lô và tắt auto bid
 *     của họ ở đó, rồi lô trở về đúng như các lượt khác cho biết. Các lượt bị xoá; {@code bid_retractions} giữ bản ghi,
 *     nhân viên đọc được.
 */
@Service
public class BidRetractions {

    public enum Reason { QUICK, TYPO }

    /**
     * EN: Whether the reader may take back their bids on a lot right now, and on what grounds. {@code blockedBy} is the
     *     error code that would come back, null when allowed.
     * VI: Người đọc có được rút các lượt của mình trên lô lúc này không, và theo lý do nào. {@code blockedBy} là mã lỗi
     *     sẽ trả về, null khi được phép.
     */
    public record Eligibility(boolean hasBids, boolean quick, Instant quickUntil, boolean typo, int usedThisMonth,
            int monthlyLimit, String blockedBy) {
    }

    /** EN: What a retraction did. / VI: Kết quả một lần rút. */
    public record Retracted(UUID auctionId, int bidsRemoved, BigDecimal priceBefore, BigDecimal currentPrice,
            int bidCount, BigDecimal minimumNextBid, Boolean reserveMet) {
    }

    /** EN: One line of the staff log. / VI: Một dòng trong nhật ký cho nhân viên. */
    public record LogEntry(UUID id, UUID auctionId, Long lotNumber, String lotName, UUID bidderId, String bidderName,
            String bidderEmail, BigDecimal amount, int bidsRemoved, Instant bidPlacedAt, String reason, String note,
            BigDecimal priceBefore, BigDecimal priceAfter, boolean wasLeading, int bidderRetractions30d,
            Instant createdAt) {
    }

    /** EN: Raised after a retraction, delivered after commit. / VI: Phát ra sau một lần rút, giao đi sau commit. */
    public record BidRetractedEvent(UUID auctionId, UUID bidderId, UUID newLeaderId, Retracted result) {
    }

    private final BidRepository bids;
    private final AutoBidRepository autoBids;
    private final AuctionService auctions;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final Duration quickWindow;
    private final Duration closingGuard;
    private final BigDecimal typoFactor;
    private final int monthlyLimit;

    BidRetractions(BidRepository bids, AutoBidRepository autoBids, AuctionService auctions, JdbcTemplate jdbc,
            ApplicationEventPublisher events,
            @Value("${nexbid.retraction.quick-window:PT5M}") Duration quickWindow,
            @Value("${nexbid.retraction.closing-guard:PT10M}") Duration closingGuard,
            @Value("${nexbid.retraction.typo-factor:2}") BigDecimal typoFactor,
            @Value("${nexbid.retraction.monthly-limit:3}") int monthlyLimit) {
        this.bids = bids;
        this.autoBids = autoBids;
        this.auctions = auctions;
        this.jdbc = jdbc;
        this.events = events;
        this.quickWindow = quickWindow;
        this.closingGuard = closingGuard;
        this.typoFactor = typoFactor;
        this.monthlyLimit = monthlyLimit;
    }

    @Transactional(readOnly = true)
    public Eligibility check(UUID auctionId, UUID bidderId, Instant now) {
        BiddingState lot = auctions.biddingState(auctionId, now);
        List<Bid> mine = bids.findByAuctionIdAndBidderIdOrderByCreatedAtDesc(auctionId, bidderId);
        int used = usedSince(bidderId, now);
        if (mine.isEmpty()) {
            return new Eligibility(false, false, null, false, used, monthlyLimit, ErrorCode.RETRACTION_NOT_ALLOWED.name());
        }
        Bid last = mine.get(0);
        Instant quickUntil = last.getCreatedAt().plus(quickWindow);
        boolean quick = now.isBefore(quickUntil);
        boolean typo = looksLikeTypo(auctionId, last);
        String blocked = blockedBy(lot, now, used, quick, typo);
        return new Eligibility(true, quick && blocked == null, quickUntil, typo && blocked == null, used, monthlyLimit,
                blocked);
    }

    /**
     * EN: Takes the bids back under the bidding lock, so no bid lands between reading the lot and rewinding it.
     * VI: Rút các lượt trong khoá trả giá, để không lượt nào chen vào giữa lúc đọc lô và lúc đưa nó về.
     */
    @Transactional
    public Retracted retract(UUID auctionId, UUID bidderId, Reason reason, String note, Instant now) {
        BiddingState lot = auctions.lockBiddingState(auctionId, now);
        List<Bid> mine = bids.findByAuctionIdAndBidderIdOrderByCreatedAtDesc(auctionId, bidderId);
        if (mine.isEmpty()) {
            throw new BusinessException(ErrorCode.RETRACTION_NOT_ALLOWED, "You have no bids on this lot");
        }
        Bid last = mine.get(0);
        boolean quick = now.isBefore(last.getCreatedAt().plus(quickWindow));
        boolean typo = looksLikeTypo(auctionId, last);
        String blocked = blockedBy(lot, now, usedSince(bidderId, now), quick, typo);
        if (blocked != null) {
            throw refusal(blocked);
        }
        if (reason == Reason.QUICK && !quick) {
            throw new BusinessException(ErrorCode.RETRACTION_NOT_ALLOWED,
                    "A bid can be taken back without a reason only in its first minutes");
        }
        if (reason == Reason.TYPO && !typo) {
            throw new BusinessException(ErrorCode.RETRACTION_NOT_ALLOWED,
                    "That bid does not look like a typing mistake");
        }

        // EN: Turned off first, so nothing bids again for them. / VI: Tắt trước, để không gì trả giá lại thay họ.
        autoBids.findByAuctionIdAndUserId(auctionId, bidderId).filter(auto -> auto.isActive()).ifPresent(auto -> {
            auto.deactivate();
            autoBids.saveAndFlush(auto);
        });

        BigDecimal highest = mine.stream().map(Bid::getAmount).max(Comparator.naturalOrder()).orElseThrow();
        bids.deleteAll(mine);
        bids.flush();

        Bid top = bids.findFirstByAuctionIdOrderByAmountDescCreatedAtAsc(auctionId).orElse(null);
        int remaining = (int) bids.countByAuctionId(auctionId);
        BiddingState after = auctions.rewindAfterRetraction(auctionId, top == null ? null : top.getBidderId(),
                top == null ? null : top.getAmount(), remaining, now);

        jdbc.update("""
                INSERT INTO bid_retractions (id, auction_id, bidder_id, amount, bids_removed, bid_placed_at, reason, note,
                                             price_before, price_after, was_leading, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), auctionId, bidderId, highest, mine.size(), Timestamp.from(last.getCreatedAt()),
                reason.name(), note == null || note.isBlank() ? null : note.strip(), lot.currentPrice(),
                after.currentPrice(), bidderId.equals(lot.leaderId()), Timestamp.from(now));

        Retracted result = new Retracted(auctionId, mine.size(), lot.currentPrice(), after.currentPrice(),
                after.bidCount(), after.minimumNextBid(), after.reserveMet());
        events.publishEvent(new BidRetractedEvent(auctionId, bidderId, after.leaderId(), result));
        return result;
    }

    /** EN: The newest retractions, for staff. / VI: Các lần rút mới nhất, cho nhân viên. */
    @Transactional(readOnly = true)
    public List<LogEntry> log(int limit, Instant now) {
        return jdbc.query("""
                SELECT r.*, a.lot_number, p.name AS lot_name, u.full_name, u.email,
                       (SELECT count(*) FROM bid_retractions x
                         WHERE x.bidder_id = r.bidder_id AND x.created_at > ?) AS recent
                  FROM bid_retractions r
                  JOIN auctions a ON a.id = r.auction_id
                  JOIN products p ON p.id = a.product_id
                  JOIN users u ON u.id = r.bidder_id
                 ORDER BY r.created_at DESC
                 LIMIT ?
                """, (rs, n) -> new LogEntry(
                rs.getObject("id", UUID.class), rs.getObject("auction_id", UUID.class), rs.getLong("lot_number"),
                rs.getString("lot_name"), rs.getObject("bidder_id", UUID.class), rs.getString("full_name"),
                rs.getString("email"), rs.getBigDecimal("amount"), rs.getInt("bids_removed"),
                rs.getTimestamp("bid_placed_at").toInstant(), rs.getString("reason"), rs.getString("note"),
                rs.getBigDecimal("price_before"), rs.getBigDecimal("price_after"), rs.getBoolean("was_leading"),
                rs.getInt("recent"), rs.getTimestamp("created_at").toInstant()),
                Timestamp.from(now.minus(Duration.ofDays(30))), Math.max(1, Math.min(limit, 500)));
    }

    private String blockedBy(BiddingState lot, Instant now, int used, boolean quick, boolean typo) {
        if (!lot.open() || lot.ended()) {
            return ErrorCode.AUCTION_NOT_ACTIVE.name();
        }
        if (!now.isBefore(lot.endTime().minus(closingGuard))) {
            return ErrorCode.RETRACTION_TOO_LATE.name();
        }
        if (used >= monthlyLimit) {
            return ErrorCode.RETRACTION_LIMIT.name();
        }
        if (!quick && !typo) {
            return ErrorCode.RETRACTION_NOT_ALLOWED.name();
        }
        return null;
    }

    private static BusinessException refusal(String code) {
        ErrorCode error = ErrorCode.valueOf(code);
        String message = switch (error) {
            case AUCTION_NOT_ACTIVE -> "This auction is not open for bidding";
            case RETRACTION_TOO_LATE -> "Bids cannot be taken back in a lot's last minutes";
            case RETRACTION_LIMIT -> "You have taken back bids too many times this month";
            default -> "This bid can no longer be taken back";
        };
        return new BusinessException(error, message);
    }

    /**
     * EN: The last bid against the price it was placed over: the highest earlier bid on the lot, or the start.
     * VI: Lượt cuối so với mức giá nó được đặt lên trên: lượt cao nhất trước đó trên lô, hoặc giá khởi điểm.
     */
    private boolean looksLikeTypo(UUID auctionId, Bid last) {
        // EN: A sealed lot's other bids are secret, so only the start counts. / VI: Các lượt khác của lô kín là bí mật, nên chỉ tính giá khởi điểm.
        BigDecimal before = auctions.isSealed(auctionId) ? null : bids.maxAmountBefore(auctionId, last.getCreatedAt());
        if (before == null) {
            before = auctions.startingPriceOf(auctionId);
        }
        return before.signum() > 0 && last.getAmount().compareTo(before.multiply(typoFactor)) >= 0;
    }

    private int usedSince(UUID bidderId, Instant now) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM bid_retractions WHERE bidder_id = ? AND created_at > ?", Integer.class,
                bidderId, Timestamp.from(now.minus(Duration.ofDays(30))));
        return count == null ? 0 : count;
    }
}
