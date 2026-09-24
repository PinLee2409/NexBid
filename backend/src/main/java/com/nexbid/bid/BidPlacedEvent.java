package com.nexbid.bid;

/**
 * EN: Raised the moment a bid is accepted, delivered only once the transaction commits. Nothing outside
 *     the database hears about a bid that might still be rolled back.
 * VI: Phát ra ngay khi một lượt trả giá được chấp nhận, nhưng chỉ giao đi sau khi transaction commit.
 *     Không ai ngoài database được nghe về một lượt vẫn còn có thể bị hoàn tác.
 */
record BidPlacedEvent(PlacedBidView placed) {
}
