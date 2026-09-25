package com.nexbid.bid;

import java.util.UUID;

import org.springframework.modulith.events.Externalized;

/**
 * EN: Raised the moment a bid is accepted, delivered only once the transaction commits. Nothing outside
 *     the database hears about a bid that might still be rolled back.
 * VI: Phát ra ngay khi một lượt trả giá được chấp nhận, nhưng chỉ giao đi sau khi transaction commit.
 *     Không ai ngoài database được nghe về một lượt vẫn còn có thể bị hoàn tác.
 */
@Externalized("nexbid.auctions::#{placed().bid().auctionId()}")
public record BidPlacedEvent(
        PlacedBidView placed,
        // EN: For consumers inside the system only; the socket broadcast never copies it.
        // VI: Chỉ dành cho consumer bên trong hệ thống; bản tin qua socket không bao giờ chép trường này.
        UUID bidderId) {
}
