package com.nexbid.bid;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.auction.LotDeposits;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.user.UserService;

/**
 * EN: Offers on lots with a buy-now price, before anyone has bid. A buyer names a price at or above the start and below
 *     the buy-now price; the seller accepts (the lot closes in the buyer's name at that price and the usual payment
 *     opens), declines, or counters with a higher price the buyer may accept. An offer lapses after two days, and every
 *     open offer stops counting once the lot gets a bid.
 * VI: Đề nghị trên lô có giá mua ngay, trước khi có ai trả giá. Người mua đưa ra một giá từ mức khởi điểm trở lên và
 *     thấp hơn giá mua ngay; người bán nhận (lô đóng đứng tên người mua với giá đó và khoản thanh toán như thường lệ được
 *     mở), từ chối, hoặc trả giá lại một mức cao hơn mà người mua có thể nhận. Đề nghị hết hạn sau hai ngày, và mọi đề
 *     nghị còn mở hết giá trị khi lô có lượt trả giá.
 */
@Service
public class LotOffers {

    static final Duration LIFETIME = Duration.ofHours(48);
    /** EN: Offers one buyer may make on one lot, counting declined ones. / VI: Số đề nghị một người mua được gửi trên một lô, tính cả lần bị từ chối. */
    static final int PER_LOT = 3;

    private final JdbcTemplate jdbc;
    private final AuctionService auctions;
    private final BidRecorder recorder;
    private final BidderRestriction restriction;
    private final LotDeposits deposits;
    private final UserService users;
    private final ApplicationEventPublisher events;

    LotOffers(JdbcTemplate jdbc, AuctionService auctions, BidRecorder recorder, BidderRestriction restriction,
            LotDeposits deposits, UserService users, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.auctions = auctions;
        this.recorder = recorder;
        this.restriction = restriction;
        this.deposits = deposits;
        this.users = users;
        this.events = events;
    }

    /**
     * EN: {@code status} is the effective one: an open offer past its time reads EXPIRED. {@code buyer} is the masked
     *     handle, the only name a seller sees before a sale.
     * VI: {@code status} là trạng thái thực tế: đề nghị còn mở mà quá hạn sẽ là EXPIRED. {@code buyer} là tên đã che,
     *     cái tên duy nhất người bán thấy trước khi bán.
     */
    public record OfferView(UUID id, UUID auctionId, String lotTitle, String buyer, BigDecimal amount,
            BigDecimal counterAmount, String status, String message, Instant expiresAt, Instant createdAt,
            Instant answeredAt, boolean mine, BigDecimal buyNowPrice) {
    }

    private record Row(UUID id, UUID auctionId, UUID buyerId, BigDecimal amount, BigDecimal counter, String status,
            String message, Instant expiresAt, Instant createdAt, Instant answeredAt) {

        boolean open(Instant now) {
            return ("PENDING".equals(status) || "COUNTERED".equals(status)) && expiresAt.isAfter(now);
        }

        String effective(Instant now) {
            return ("PENDING".equals(status) || "COUNTERED".equals(status)) && !expiresAt.isAfter(now)
                    ? "EXPIRED" : status;
        }
    }

    @Transactional
    public OfferView make(UUID buyerId, UUID auctionId, BigDecimal amount, String message) {
        Instant now = Instant.now();
        AuctionService.OfferTerms terms = auctions.offerTermsOf(auctionId);
        if (terms.sellerId().equals(buyerId)) {
            throw new BusinessException(ErrorCode.SELLER_CANNOT_BID, "You cannot make an offer on your own lot");
        }
        if (!auctions.takesOffers(auctionId, now)) {
            throw new BusinessException(ErrorCode.OFFER_UNAVAILABLE,
                    "This lot takes offers only while it is open, has a buy-now price and no bids yet");
        }
        if (amount.compareTo(terms.startingPrice()) < 0 || amount.compareTo(terms.buyNowPrice()) >= 0
                || amount.stripTrailingZeros().scale() > 0) {
            throw new BusinessException(ErrorCode.OFFER_INVALID,
                    "An offer is a whole amount from the starting price up to just below the buy-now price");
        }
        restriction.check(buyerId, amount);
        deposits.require(auctionId, buyerId);
        // EN: Locks this buyer's offers on the lot, so two quick posts cannot both slip under the limit.
        // VI: Khoá các đề nghị của người mua trên lô, để hai lần gửi liền nhau không cùng lọt dưới giới hạn.
        List<Row> earlier = jdbc.query("SELECT * FROM lot_offers WHERE auction_id = ? AND buyer_id = ? FOR UPDATE",
                (rs, n) -> row(rs), auctionId, buyerId);
        if (earlier.size() >= PER_LOT) {
            throw new BusinessException(ErrorCode.OFFER_LIMIT, "You can make at most " + PER_LOT + " offers on a lot");
        }
        // EN: A new offer replaces the buyer's open one, including a counter they did not take.
        // VI: Đề nghị mới thay cho đề nghị còn mở của người mua, kể cả lời trả giá lại mà họ chưa nhận.
        jdbc.update("""
                UPDATE lot_offers SET status = 'WITHDRAWN', answered_at = ?
                WHERE auction_id = ? AND buyer_id = ? AND status IN ('PENDING', 'COUNTERED')
                """, utc(now), auctionId, buyerId);
        UUID id = UUID.randomUUID();
        String note = message == null || message.isBlank() ? null : message.strip();
        Instant expires = now.plus(LIFETIME).isAfter(terms.endTime()) ? terms.endTime() : now.plus(LIFETIME);
        try {
            jdbc.update("""
                    INSERT INTO lot_offers (id, auction_id, buyer_id, amount, status, message, expires_at, created_at)
                    VALUES (?, ?, ?, ?, 'PENDING', ?, ?, ?)
                    """, id, auctionId, buyerId, amount, note, utc(expires), utc(now));
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.OFFER_LIMIT, "You already have an open offer on this lot");
        }
        events.publishEvent(new LotOfferEvents.Made(id, auctionId, terms.sellerId(), buyerId, amount, now));
        return view(load(id).orElseThrow(), buyerId, now);
    }

    /** EN: The seller accepts a waiting offer. / VI: Người bán nhận một đề nghị đang chờ. */
    @Transactional
    public OfferView accept(UUID sellerId, UUID offerId) {
        Row offer = lockedForSeller(sellerId, offerId);
        requireOpen(offer, "PENDING");
        return sell(offer, offer.amount(), offer.buyerId());
    }

    /** EN: The seller declines a waiting offer. / VI: Người bán từ chối một đề nghị đang chờ. */
    @Transactional
    public OfferView decline(UUID sellerId, UUID offerId) {
        Row offer = lockedForSeller(sellerId, offerId);
        requireOpen(offer, "PENDING");
        return close(offer, "DECLINED", sellerId);
    }

    /** EN: The seller answers with a higher price, below buy-now; the buyer has two days to take it. / VI: Người bán trả giá lại cao hơn, dưới giá mua ngay; người mua có hai ngày để nhận. */
    @Transactional
    public OfferView counter(UUID sellerId, UUID offerId, BigDecimal price) {
        Row offer = lockedForSeller(sellerId, offerId);
        requireOpen(offer, "PENDING");
        AuctionService.OfferTerms terms = auctions.offerTermsOf(offer.auctionId());
        if (price.compareTo(offer.amount()) <= 0 || price.compareTo(terms.buyNowPrice()) >= 0
                || price.stripTrailingZeros().scale() > 0) {
            throw new BusinessException(ErrorCode.OFFER_INVALID,
                    "A counter is a whole amount above the offer and below the buy-now price");
        }
        Instant now = Instant.now();
        Instant expires = now.plus(LIFETIME).isAfter(terms.endTime()) ? terms.endTime() : now.plus(LIFETIME);
        jdbc.update("""
                UPDATE lot_offers SET status = 'COUNTERED', counter_amount = ?, expires_at = ?, answered_at = ?
                WHERE id = ?
                """, price, utc(expires), utc(now), offerId);
        events.publishEvent(new LotOfferEvents.Answered(offerId, offer.auctionId(), sellerId, offer.buyerId(),
                "COUNTERED", price, now));
        return view(load(offerId).orElseThrow(), sellerId, now);
    }

    /** EN: The buyer takes the seller's counter: the lot is sold at it. / VI: Người mua nhận giá người bán trả lại: lô được bán ở giá đó. */
    @Transactional
    public OfferView acceptCounter(UUID buyerId, UUID offerId) {
        Row offer = lockedForBuyer(buyerId, offerId);
        requireOpen(offer, "COUNTERED");
        return sell(offer, offer.counter(), buyerId);
    }

    /** EN: The buyer turns the counter down. / VI: Người mua từ chối giá trả lại. */
    @Transactional
    public OfferView declineCounter(UUID buyerId, UUID offerId) {
        Row offer = lockedForBuyer(buyerId, offerId);
        requireOpen(offer, "COUNTERED");
        return close(offer, "DECLINED", buyerId);
    }

    /** EN: The buyer takes back an offer the seller has not answered. / VI: Người mua rút lại đề nghị người bán chưa trả lời. */
    @Transactional
    public OfferView withdraw(UUID buyerId, UUID offerId) {
        Row offer = lockedForBuyer(buyerId, offerId);
        requireOpen(offer, "PENDING");
        return close(offer, "WITHDRAWN", buyerId);
    }

    /** EN: Offers on the seller's lots, open ones first. / VI: Các đề nghị trên lô của người bán, đề nghị còn mở trước. */
    @Transactional(readOnly = true)
    public List<OfferView> forSeller(UUID sellerId) {
        return views(jdbc.query("""
                SELECT o.* FROM lot_offers o JOIN auctions a ON a.id = o.auction_id
                WHERE a.seller_id = ?
                ORDER BY (o.status IN ('PENDING', 'COUNTERED') AND o.expires_at > now()) DESC, o.created_at DESC
                LIMIT 200
                """, (rs, n) -> row(rs), sellerId), sellerId);
    }

    /** EN: The buyer's own offers, newest first. / VI: Các đề nghị của người mua, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<OfferView> forBuyer(UUID buyerId) {
        return views(jdbc.query("SELECT * FROM lot_offers WHERE buyer_id = ? ORDER BY created_at DESC LIMIT 200",
                (rs, n) -> row(rs), buyerId), buyerId);
    }

    /** EN: The buyer's offers on one lot, for the lot page. / VI: Các đề nghị của người mua trên một lô, cho trang lô. */
    @Transactional(readOnly = true)
    public List<OfferView> forBuyerOnLot(UUID buyerId, UUID auctionId) {
        return views(jdbc.query(
                "SELECT * FROM lot_offers WHERE buyer_id = ? AND auction_id = ? ORDER BY created_at DESC",
                (rs, n) -> row(rs), buyerId, auctionId), buyerId);
    }

    private OfferView sell(Row offer, BigDecimal price, UUID buyerId) {
        restriction.check(buyerId, price);
        deposits.require(offer.auctionId(), buyerId);
        AuctionService.AcceptedBid accepted = auctions.sellByOffer(offer.auctionId(), buyerId, price);
        recorder.recordAccepted(accepted, buyerId);
        auctions.closeSoldByOffer(offer.auctionId(), buyerId, accepted.acceptedAt());
        Instant now = accepted.acceptedAt();
        jdbc.update("UPDATE lot_offers SET status = 'ACCEPTED', answered_at = ? WHERE id = ?", utc(now), offer.id());
        // EN: The lot is sold; every other open offer on it is answered too. / VI: Lô đã bán; mọi đề nghị còn mở khác trên lô cũng được trả lời.
        jdbc.update("""
                UPDATE lot_offers SET status = 'DECLINED', answered_at = ?
                WHERE auction_id = ? AND id <> ? AND status IN ('PENDING', 'COUNTERED')
                """, utc(now), offer.auctionId(), offer.id());
        UUID sellerId = auctions.sellerOf(offer.auctionId());
        events.publishEvent(new LotOfferEvents.Answered(offer.id(), offer.auctionId(), sellerId, buyerId, "ACCEPTED",
                price, now));
        return view(load(offer.id()).orElseThrow(), buyerId, now);
    }

    private OfferView close(Row offer, String status, UUID actorId) {
        Instant now = Instant.now();
        jdbc.update("UPDATE lot_offers SET status = ?, answered_at = ? WHERE id = ?", status, utc(now), offer.id());
        if (!"WITHDRAWN".equals(status)) {
            UUID sellerId = auctions.sellerOf(offer.auctionId());
            boolean bySeller = actorId.equals(sellerId);
            events.publishEvent(new LotOfferEvents.Answered(offer.id(), offer.auctionId(), sellerId, offer.buyerId(),
                    bySeller ? "DECLINED" : "COUNTER_DECLINED", offer.amount(), now));
        }
        return view(load(offer.id()).orElseThrow(), actorId, now);
    }

    private void requireOpen(Row offer, String expected) {
        Instant now = Instant.now();
        if (!expected.equals(offer.status()) || !offer.open(now)) {
            throw new BusinessException(ErrorCode.OFFER_UNAVAILABLE, "This offer is no longer open");
        }
    }

    private Row lockedForSeller(UUID sellerId, UUID offerId) {
        return jdbc.query("SELECT * FROM lot_offers WHERE id = ? FOR UPDATE", (rs, n) -> row(rs), offerId).stream()
                .findFirst()
                .filter(found -> auctions.sellerOf(found.auctionId()).equals(sellerId))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.OFFER_NOT_FOUND, "No offer " + offerId));
    }

    private Row lockedForBuyer(UUID buyerId, UUID offerId) {
        return jdbc.query("SELECT * FROM lot_offers WHERE id = ? FOR UPDATE", (rs, n) -> row(rs), offerId).stream()
                .findFirst()
                .filter(found -> found.buyerId().equals(buyerId))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.OFFER_NOT_FOUND, "No offer " + offerId));
    }

    private Optional<Row> load(UUID id) {
        return jdbc.query("SELECT * FROM lot_offers WHERE id = ?", (rs, n) -> row(rs), id).stream().findFirst();
    }

    private List<OfferView> views(List<Row> rows, UUID viewerId) {
        Instant now = Instant.now();
        Map<UUID, AuctionSummaryView> lots = auctions.participantSummariesOf(
                rows.stream().map(Row::auctionId).distinct().toList());
        Map<UUID, String> names = users.namesOf(rows.stream().map(Row::buyerId).distinct().toList());
        return rows.stream().map(row -> {
            AuctionSummaryView lot = lots.get(row.auctionId());
            return new OfferView(row.id(), row.auctionId(), lot == null ? "" : lot.product().name(),
                    UserService.handleOf(names.get(row.buyerId())), row.amount(), row.counter(), row.effective(now),
                    row.message(), row.expiresAt(), row.createdAt(), row.answeredAt(), row.buyerId().equals(viewerId),
                    lot == null ? null : lot.auction().buyNowPrice());
        }).toList();
    }

    private OfferView view(Row row, UUID viewerId, Instant now) {
        return views(List.of(row), viewerId).getFirst();
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getObject("auction_id", UUID.class),
                rs.getObject("buyer_id", UUID.class), rs.getBigDecimal("amount"), rs.getBigDecimal("counter_amount"),
                rs.getString("status"), rs.getString("message"), instant(rs.getTimestamp("expires_at")),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("answered_at")));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Object utc(Instant at) {
        return at.atOffset(ZoneOffset.UTC);
    }
}
