package com.nexbid.mail;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * EN: One new lot in the daily digest. {@code why} is the saved search's words or the followed seller's handle.
 * VI: Một lô mới trong email tổng hợp hằng ngày. {@code why} là từ khoá của bộ lọc đã lưu hoặc tên người bán đang theo dõi.
 */
public record DigestLot(UUID auctionId, String title, BigDecimal price, Instant endsAt, String why) {
}
