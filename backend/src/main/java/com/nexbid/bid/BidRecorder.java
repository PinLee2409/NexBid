package com.nexbid.bid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.nexbid.auction.AuctionService;
import com.nexbid.bid.entity.Bid;
import com.nexbid.bid.repository.BidRepository;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.user.UserService;

/**
 * EN: Records one bid, whoever placed it — a person or their auto bid. One path for both, so an automatic
 *     bid passes exactly the checks, the lock and the anti-sniping rule a manual one does.
 * VI: Ghi một lượt trả giá, bất kể ai đặt — một người hay auto bid của họ. Cả hai đi chung một đường, nên
 *     lượt tự động qua đúng những bước kiểm, khoá và luật chống bid phút chót như lượt thủ công.
 */
@Component
class BidRecorder {

    private final BidRepository bids;
    private final AuctionService auctions;
    private final UserService users;
    private final ApplicationEventPublisher events;

    BidRecorder(BidRepository bids, AuctionService auctions, UserService users, ApplicationEventPublisher events) {
        this.bids = bids;
        this.auctions = auctions;
        this.users = users;
        this.events = events;
    }

    /**
     * EN: The tracker notes who led before this bid, so the outbid can be told once everything settles.
     * VI: Bộ theo dõi ghi lại ai dẫn trước lượt này, để báo người bị vượt giá khi mọi thứ đã ổn định.
     */
    PlacedBidView record(UUID auctionId, UUID bidderId, BigDecimal amount, Instant at, LeadTracker leads) {
        AuctionService.AcceptedBid accepted = auctions.acceptBid(auctionId, bidderId, amount, at);
        leads.saw(accepted.previousLeaderId(), bidderId);

        Bid bid;
        try {
            bid = bids.saveAndFlush(new Bid(auctionId, bidderId, amount, accepted.acceptedAt()));
        } catch (DataIntegrityViolationException ex) {
            // EN: Two bids of the same amount on one lot. The price only rises, so an equal amount means
            //     one of them was worked out from a price that has already moved.
            // VI: Hai lượt cùng số tiền trên một lô. Giá chỉ tăng, nên hai số bằng nhau nghĩa là một trong
            //     hai được tính từ mức giá đã thay đổi rồi.
            throw new BusinessException(
                    ErrorCode.BID_CONFLICT, "Someone else bid that amount first; please try again");
        }

        String mask = users.findById(bidderId).map(account -> BidService.mask(account.fullName())).orElse("***");

        PlacedBidView placed = new PlacedBidView(
                new BidView(bid.getId(), auctionId, mask, true, bid.getAmount(), bid.getCreatedAt()),
                accepted.currentPrice(),
                accepted.bidCount(),
                accepted.minimumNextBid(),
                accepted.endTime(),
                Instant.now(),
                true);

        // EN: Raised here, delivered after commit (guide §23). If the socket is down the bid still stands.
        // VI: Phát ra ở đây, giao đi sau commit (guide §23). Socket có hỏng thì lượt trả giá vẫn có hiệu lực.
        events.publishEvent(new BidPlacedEvent(placed, bidderId));

        return placed;
    }
}
