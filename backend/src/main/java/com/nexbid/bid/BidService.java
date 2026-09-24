package com.nexbid.bid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.BiddingState;
import com.nexbid.auction.PageView;
import com.nexbid.bid.dto.BidHistoryQuery;
import com.nexbid.bid.entity.Bid;
import com.nexbid.bid.repository.BidRepository;
import com.nexbid.user.UserService;

/**
 * EN: Placing a bid (guide §20). The auction module decides whether the offer is allowed and moves the
 *     price; this module writes the record of it. One transaction covers both, so a price never rises
 *     without a bid to account for it.
 * VI: Đặt giá (guide §20). Module auction quyết định lượt đó có được phép không và đẩy giá; module này ghi
 *     lại bản ghi. Cả hai nằm trong một transaction, nên giá không bao giờ tăng mà thiếu một lượt trả giá
 *     giải thích cho nó.
 */
@Service
public class BidService {

    private final BidRepository bids;
    private final AuctionService auctions;
    private final UserService users;
    private final BidRecorder recorder;
    private final ProxyBidding proxies;

    BidService(
            BidRepository bids,
            AuctionService auctions,
            UserService users,
            BidRecorder recorder,
            ProxyBidding proxies) {

        this.bids = bids;
        this.auctions = auctions;
        this.users = users;
        this.recorder = recorder;
        this.proxies = proxies;
    }

    /**
     * EN: Records the bid, lets any auto bids answer it (guide §31), and reports where the lot ended up —
     *     all in one transaction, so nobody ever sees the price between a bid and its answer.
     * VI: Ghi lượt trả giá, để các auto bid đáp trả (guide §31), rồi báo lô dừng ở đâu — tất cả trong một
     *     transaction, nên không ai thấy mức giá ở giữa một lượt trả giá và lần đáp trả nó.
     */
    @Transactional
    public PlacedBidView place(UUID auctionId, UUID bidderId, BigDecimal amount) {
        LeadTracker leads = new LeadTracker();
        PlacedBidView mine = recorder.record(auctionId, bidderId, amount, Instant.now(), leads);

        BiddingState lot = proxies.settleAndAnnounce(auctionId, mine.bid().createdAt(), leads);

        return new PlacedBidView(
                mine.bid(),
                lot.currentPrice(),
                lot.bidCount(),
                lot.minimumNextBid(),
                lot.endTime(),
                Instant.now(),
                bidderId.equals(lot.leaderId()));
    }

    /**
     * EN: Public bid history, newest first (guide §22). Names are masked for everyone, including the
     *     caller's own — the flag tells the page which line is theirs without printing anybody's name.
     * VI: Lịch sử trả giá công khai, mới nhất trước (guide §22). Tên ai cũng bị che, kể cả của chính người
     *     gọi — cờ đánh dấu cho trang biết dòng nào là của họ mà không cần in tên ai ra.
     */
    public PageView<BidView> historyOf(UUID auctionId, UUID viewerId, BidHistoryQuery query) {
        // EN: A lot the catalogue hides has no public history either.
        // VI: Lô mà danh mục đã giấu thì cũng không có lịch sử công khai.
        auctions.requirePubliclyVisible(auctionId);

        Page<Bid> page = bids.findByAuctionIdOrderByCreatedAtDescAmountDesc(
                auctionId, PageRequest.of(query.zeroBasedPage(), query.size()));

        // EN: One query for every name on the page, not one per row.
        // VI: Một truy vấn cho mọi cái tên trên trang, không phải mỗi dòng một lần.
        Map<UUID, String> names = users.namesOf(
                page.getContent().stream().map(Bid::getBidderId).distinct().toList());

        return PageView.of(page, bid -> new BidView(
                bid.getId(),
                auctionId,
                mask(names.get(bid.getBidderId())),
                bid.getBidderId().equals(viewerId),
                bid.getAmount(),
                bid.getCreatedAt()));
    }

    /**
     * EN: Everyone who bid on a lot at least once, for telling the ones who did not win.
     * VI: Mọi người đã trả giá lô này ít nhất một lần, để báo cho những ai không thắng.
     */
    public List<UUID> biddersOf(UUID auctionId) {
        return bids.findDistinctBidderIds(auctionId);
    }

    /**
     * EN: "Nguyen Van A" becomes "ngu***" (spec §37). Enough for a bidder to recognise their own line,
     *     not enough for anyone else to work out who they are.
     * VI: "Nguyen Van A" thành "ngu***" (spec §37). Đủ để người trả giá nhận ra dòng của mình, không đủ để
     *     người khác suy ra họ là ai.
     */
    static String mask(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "***";
        }

        String head = trimmed.length() <= 3 ? trimmed : trimmed.substring(0, 3);
        return head.toLowerCase(Locale.ROOT) + "***";
    }
}
