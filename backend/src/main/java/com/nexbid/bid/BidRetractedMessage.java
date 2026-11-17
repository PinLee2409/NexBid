package com.nexbid.bid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * EN: Sent to a lot's room when someone took their bids back: the figures the lot now stands at.
 * VI: Gửi tới phòng của lô khi có người rút lại các lượt của mình: các con số lô đang đứng hiện giờ.
 */
public record BidRetractedMessage(
        String type,
        UUID auctionId,
        BigDecimal currentPrice,
        int bidCount,
        BigDecimal minimumNextBid,
        Boolean reserveMet,
        Instant serverTime) {

    public static final String TYPE = "BID_RETRACTED";
}
