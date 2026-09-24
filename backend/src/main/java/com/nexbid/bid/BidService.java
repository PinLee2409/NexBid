package com.nexbid.bid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionService;
import com.nexbid.bid.entity.Bid;
import com.nexbid.bid.repository.BidRepository;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
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

    public BidService(BidRepository bids, AuctionService auctions, UserService users) {
        this.bids = bids;
        this.auctions = auctions;
        this.users = users;
    }

    @Transactional
    public PlacedBidView place(UUID auctionId, UUID bidderId, BigDecimal amount) {
        AuctionService.AcceptedBid accepted = auctions.acceptBid(auctionId, bidderId, amount);

        Bid bid;
        try {
            bid = bids.saveAndFlush(new Bid(auctionId, bidderId, amount));
        } catch (DataIntegrityViolationException ex) {
            // EN: Two bids of the same amount on one lot. The price only rises, so an equal amount means
            //     one of them was worked out from a price that has already moved.
            // VI: Hai lượt cùng số tiền trên một lô. Giá chỉ tăng, nên hai số bằng nhau nghĩa là một trong
            //     hai được tính từ mức giá đã thay đổi rồi.
            throw new BusinessException(
                    ErrorCode.BID_CONFLICT, "Someone else bid that amount first; please try again");
        }

        String mask = users.findById(bidderId).map(account -> mask(account.fullName())).orElse("***");

        return new PlacedBidView(
                new BidView(bid.getId(), auctionId, mask, true, bid.getAmount(), bid.getCreatedAt()),
                accepted.currentPrice(),
                accepted.bidCount(),
                accepted.minimumNextBid(),
                accepted.endTime(),
                Instant.now());
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
