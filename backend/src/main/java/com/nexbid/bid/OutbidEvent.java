package com.nexbid.bid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.modulith.events.Externalized;

/**
 * EN: People who lost the lead on a lot, raised once a bid and all its automatic answers have settled
 *     and delivered after commit. {@code currentPrice} is the price they now have to beat.
 * VI: Những người vừa mất vị trí dẫn đầu trên một lô, phát ra khi một lượt trả giá và mọi lần đáp trả tự
 *     động đã ổn định, giao đi sau commit. {@code currentPrice} là mức giá họ giờ phải vượt qua.
 */
@Externalized("nexbid.auctions::#{auctionId()}")
public record OutbidEvent(UUID auctionId, Set<UUID> outbid, BigDecimal currentPrice, Instant at) {
}
