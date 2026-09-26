package com.nexbid.bid;

import java.math.BigDecimal;
import java.time.Instant;

import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.auction.BidStanding;

/**
 * EN: One lot on the "My bids" page (spec §27): the lot, the caller's best bid and where they stand.
 * VI: Một lô trên trang "Lượt trả giá của tôi" (spec §27): lô đó, lượt cao nhất của người gọi và vị thế của họ.
 */
public record MyBidView(
        AuctionSummaryView auction,
        BigDecimal yourBid,
        Instant yourLastBidAt,
        BidStanding standing) {
}
