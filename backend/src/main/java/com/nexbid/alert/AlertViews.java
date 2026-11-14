package com.nexbid.alert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * EN: What the alerts page shows.
 * VI: Những gì trang theo dõi hiển thị.
 */
public final class AlertViews {

    private AlertViews() {
    }

    /** EN: A saved catalogue search; empty lists and nulls mean "any". / VI: Một bộ lọc đã lưu; danh sách rỗng và null là "bất kỳ". */
    public record SavedSearch(UUID id, String q, List<String> category, List<String> condition, BigDecimal minPrice,
            BigDecimal maxPrice, Instant createdAt) {
    }

    /** EN: A followed seller, by public handle. / VI: Một người bán đang theo dõi, theo tên hiển thị công khai. */
    public record FollowedSeller(UUID sellerId, String displayName, Instant since) {
    }

    public record FollowState(UUID sellerId, boolean following) {
    }
}
