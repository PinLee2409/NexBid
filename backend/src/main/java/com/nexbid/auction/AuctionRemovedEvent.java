package com.nexbid.auction;

import java.time.Instant;
import java.util.UUID;

/**
 * EN: An admin took down a scheduled or live lot; the seller and its bidders are told, with the reason.
 * VI: Admin đã gỡ một lô đang chờ mở hoặc đang mở; người bán và những người đã trả giá được báo, kèm lý do.
 */
public record AuctionRemovedEvent(UUID auctionId, UUID sellerId, UUID actorId, String reason, Instant at) {
}
