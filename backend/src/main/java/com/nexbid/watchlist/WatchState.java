package com.nexbid.watchlist;

import java.util.UUID;

/**
 * EN: Whether the caller is watching a lot — what the ♡ / ♥ button needs after a tap.
 * VI: Người gọi có đang theo dõi một lô hay không — thứ nút ♡ / ♥ cần sau mỗi lần bấm.
 */
public record WatchState(UUID auctionId, boolean watching) {
}
