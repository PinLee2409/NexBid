package com.nexbid.bid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.BiddingState;
import com.nexbid.bid.entity.AutoBid;
import com.nexbid.bid.repository.AutoBidRepository;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;

/**
 * EN: Setting, changing and cancelling an auto bid (guide §31, spec §14). Setting one acts at once: if
 *     someone else leads, it bids for its owner straight away.
 * VI: Đặt, đổi và huỷ auto bid (guide §31, spec §14). Đặt xong là có hiệu lực ngay: nếu người khác đang
 *     dẫn, nó trả giá thay chủ nhân liền.
 */
@Service
public class AutoBidService {

    private final AutoBidRepository autoBids;
    private final AuctionService auctions;
    private final ProxyBidding proxies;

    AutoBidService(AutoBidRepository autoBids, AuctionService auctions, ProxyBidding proxies) {
        this.autoBids = autoBids;
        this.auctions = auctions;
        this.proxies = proxies;
    }

    @Transactional
    public AutoBidView create(UUID userId, UUID auctionId, BigDecimal maxAmount) {
        Instant now = Instant.now();
        BiddingState lot = requireBiddable(auctionId, userId, now);

        AutoBid autoBid = autoBids.findByAuctionIdAndUserId(auctionId, userId).orElse(null);
        if (autoBid != null && autoBid.isActive()) {
            throw new BusinessException(
                    ErrorCode.AUTO_BID_EXISTS, "You already have an auto bid on this lot; change it instead");
        }

        requireCeiling(maxAmount, lot);

        if (autoBid == null) {
            autoBid = new AutoBid(auctionId, userId, maxAmount);
        } else {
            autoBid.activate(maxAmount);
        }
        autoBids.saveAndFlush(autoBid);

        proxies.settleAndAnnounce(auctionId, now, new LeadTracker());
        return view(autoBid, now);
    }

    @Transactional
    public AutoBidView update(UUID userId, UUID auctionId, BigDecimal maxAmount) {
        Instant now = Instant.now();
        BiddingState lot = requireBiddable(auctionId, userId, now);

        AutoBid autoBid = autoBids.findByAuctionIdAndUserId(auctionId, userId)
                .filter(AutoBid::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.AUTO_BID_NOT_FOUND, "You have no auto bid on this lot"));

        requireCeiling(maxAmount, lot);
        autoBid.activate(maxAmount);
        autoBids.saveAndFlush(autoBid);

        proxies.settleAndAnnounce(auctionId, now, new LeadTracker());
        return view(autoBid, now);
    }

    /**
     * EN: Switches it off; bids it already placed stand. Idempotent, and the same answer whether or not
     *     there was one, so it reveals nothing.
     * VI: Tắt nó đi; những lượt nó đã trả vẫn giữ nguyên. Gọi nhiều lần vẫn vậy, và trả lời như nhau dù
     *     có hay không, nên không tiết lộ gì.
     */
    @Transactional
    public AutoBidView cancel(UUID userId, UUID auctionId) {
        AutoBid autoBid = autoBids.findByAuctionIdAndUserId(auctionId, userId).orElse(null);
        Instant now = Instant.now();

        if (autoBid == null) {
            return new AutoBidView(auctionId, null, false, null, null, false, now);
        }

        // EN: Under the bidding lock, so a bid being settled right now either sees it on or sees it off.
        // VI: Trong khoá trả giá, để lượt đang được xử lý hoặc thấy nó còn bật, hoặc thấy nó đã tắt.
        auctions.lockBiddingState(auctionId, now);
        autoBid.deactivate();
        autoBids.saveAndFlush(autoBid);

        return view(autoBid, now);
    }

    @Transactional(readOnly = true)
    public AutoBidView mine(UUID userId, UUID auctionId) {
        AutoBid autoBid = autoBids.findByAuctionIdAndUserId(auctionId, userId)
                .filter(AutoBid::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.AUTO_BID_NOT_FOUND, "You have no auto bid on this lot"));

        return view(autoBid, Instant.now());
    }

    /** EN: The same checks a manual bid meets, in the same order. / VI: Đúng các bước kiểm của lượt thủ công, đúng thứ tự. */
    private BiddingState requireBiddable(UUID auctionId, UUID userId, Instant now) {
        BiddingState lot = auctions.lockBiddingState(auctionId, now);

        if (lot.ended()) {
            throw new BusinessException(ErrorCode.AUCTION_ALREADY_ENDED, "This auction has already ended");
        }
        if (!lot.open()) {
            throw new BusinessException(ErrorCode.AUCTION_NOT_ACTIVE, "This auction is not open for bidding");
        }
        if (userId.equals(lot.sellerId())) {
            throw new BusinessException(ErrorCode.SELLER_CANNOT_BID, "You cannot bid on your own auction");
        }

        return lot;
    }

    /** EN: Spec §14: the ceiling must reach at least the next minimum. / VI: Spec §14: mức trần ít nhất phải bằng mức tối thiểu kế tiếp. */
    private static void requireCeiling(BigDecimal maxAmount, BiddingState lot) {
        if (maxAmount.compareTo(lot.minimumNextBid()) < 0) {
            throw new BusinessException(
                    ErrorCode.AUTO_BID_INVALID,
                    "The max amount must be at least " + lot.minimumNextBid().toPlainString());
        }
    }

    private AutoBidView view(AutoBid autoBid, Instant now) {
        BiddingState lot = auctions.biddingState(autoBid.getAuctionId(), now);

        return new AutoBidView(
                autoBid.getAuctionId(),
                autoBid.getMaxAmount(),
                autoBid.isActive(),
                lot.currentPrice(),
                lot.minimumNextBid(),
                autoBid.getUserId().equals(lot.leaderId()),
                autoBid.getUpdatedAt());
    }
}
