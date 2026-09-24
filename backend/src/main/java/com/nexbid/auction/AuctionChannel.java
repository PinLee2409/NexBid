package com.nexbid.auction;

import java.util.UUID;

/**
 * EN: The realtime channel for one lot (guide §23). Named here because the channel belongs to the auction,
 *     not to whichever module happens to have news — bids today, extensions and the closing bell later.
 * VI: Kênh realtime của một lô (guide §23). Đặt tên ở đây vì kênh thuộc về phiên đấu giá, không thuộc về
 *     module nào tình cờ có tin — hôm nay là lượt trả giá, sau này là gia hạn và tiếng búa cuối.
 */
public final class AuctionChannel {

    public static final String TOPIC_PREFIX = "/topic/auctions/";

    private AuctionChannel() {
    }

    public static String topicFor(UUID auctionId) {
        return TOPIC_PREFIX + auctionId;
    }
}
