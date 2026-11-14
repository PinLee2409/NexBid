package com.nexbid.bid;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.user.BuyerStanding;

/**
 * EN: Someone who has won and not paid {@code unpaidWins} times or more may still bid, but not {@code fromAmount} or
 *     above: on costly lots a walk-away hurts the seller most. Checked for bids, Buy Now and auto-bid ceilings.
 * VI: Người đã thắng mà không trả tiền từ {@code unpaidWins} lần trở lên vẫn được trả giá, nhưng không được từ
 *     {@code fromAmount} trở lên: ở lô đắt tiền, bỏ không trả gây hại cho người bán nhiều nhất. Áp dụng cho trả giá, mua
 *     ngay và mức trần auto-bid.
 */
@Component
public class BidderRestriction {

    private final BuyerStanding standing;
    private final int unpaidWins;
    private final BigDecimal fromAmount;

    BidderRestriction(BuyerStanding standing,
            @Value("${nexbid.bid.restriction.unpaid-wins}") int unpaidWins,
            @Value("${nexbid.bid.restriction.from-amount}") BigDecimal fromAmount) {
        this.standing = standing;
        this.unpaidWins = unpaidWins;
        this.fromAmount = fromAmount;
    }

    public void check(UUID bidderId, BigDecimal amount) {
        if (amount == null || amount.compareTo(fromAmount) < 0) {
            return;
        }
        if (standing.unpaidWinsOf(bidderId) >= unpaidWins) {
            throw new BusinessException(ErrorCode.BIDDER_RESTRICTED,
                    "You have left " + unpaidWins + " or more won lots unpaid, so you cannot bid "
                            + fromAmount.toPlainString() + " or more");
        }
    }
}
