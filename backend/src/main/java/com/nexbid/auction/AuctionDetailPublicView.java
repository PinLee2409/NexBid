package com.nexbid.auction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nexbid.product.CategoryView;
import com.nexbid.product.ProductImageView;

/**
 * EN: The public lot page (guide §19, spec §7.7).
 * VI: Trang lô công khai (guide §19, spec §7.7).
 *
 * <p>EN: {@code serverTime} is here because the server owns the clock (spec §11). A browser with a wrong
 *     system time must still draw the right countdown, so it measures against this rather than its own now.
 * <p>VI: Có {@code serverTime} vì đồng hồ thuộc về server (spec §11). Trình duyệt có giờ hệ thống sai vẫn
 *     phải vẽ đúng đồng hồ đếm ngược, nên nó đo theo mốc này thay vì giờ của chính nó.
 */
public record AuctionDetailPublicView(
        AuctionView auction,
        Product product,
        CategoryView category,
        List<ProductImageView> images,
        Seller seller,
        BigDecimal minimumNextBid,
        boolean openForBidding,
        Instant serverTime) {

    public record Product(UUID id, String name, String description, String condition) {
    }

    public record Seller(UUID id, String displayName) {
    }
}
