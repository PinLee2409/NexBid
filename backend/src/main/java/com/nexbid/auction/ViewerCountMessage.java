package com.nexbid.auction;

import java.util.UUID;

/**
 * EN: How many people have a lot's page open right now (spec §20.3), sent on the lot's channel.
 * VI: Có bao nhiêu người đang mở trang của một lô (spec §20.3), gửi trên kênh của lô đó.
 */
public record ViewerCountMessage(String type, UUID auctionId, int viewerCount) {

    public static final String TYPE = "VIEWER_COUNT";
}
