package com.nexbid.auction.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.nexbid.auction.AuctionStatus;
import com.nexbid.auction.entity.Auction;

/**
 * EN: The filters the browse page offers (guide §18). Each returns null when its parameter is absent, and
 *     {@link #allOf} drops those — so an unused filter never reaches the generated SQL.
 * VI: Các bộ lọc trang duyệt hàng cung cấp (guide §18). Mỗi cái trả null khi tham số vắng mặt, và
 *     {@link #allOf} loại chúng đi — nên bộ lọc không dùng tới không bao giờ lọt vào câu SQL sinh ra.
 */
public final class AuctionSpecifications {

    private AuctionSpecifications() {
    }

    /**
     * EN: Combines the filters that are actually present. Spring Data JPA 4 refuses a null in {@code and},
     *     so the nulls are dropped here rather than each caller guarding every line.
     * VI: Gộp những bộ lọc thật sự có mặt. Spring Data JPA 4 từ chối null trong {@code and}, nên loại null
     *     ở đây thay vì bắt mỗi nơi gọi phải tự canh từng dòng.
     */
    @SafeVarargs
    public static Specification<Auction> allOf(Specification<Auction>... specifications) {
        Specification<Auction> combined = null;

        for (Specification<Auction> specification : specifications) {
            if (specification == null) {
                continue;
            }
            combined = combined == null ? specification : combined.and(specification);
        }

        return combined;
    }

    /**
     * EN: What the public may see at all. Everything before approval, and everything an admin refused,
     *     stays invisible — this is the completion criterion of function 17.
     * VI: Những gì công chúng được phép nhìn thấy. Mọi thứ trước khi duyệt, và mọi thứ admin từ chối, đều
     *     vô hình — đây chính là tiêu chí hoàn thành của chức năng 17.
     */
    public static Specification<Auction> publiclyVisible() {
        return (root, query, cb) -> root.get("status").in(
                AuctionStatus.SCHEDULED,
                AuctionStatus.ACTIVE,
                AuctionStatus.ENDED,
                AuctionStatus.COMPLETED);
    }

    public static Specification<Auction> statusIn(Collection<AuctionStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Auction> productIn(Collection<UUID> productIds) {
        if (productIds == null) {
            return null;
        }
        if (productIds.isEmpty()) {
            // EN: A category filter that matched no products must return nothing, not everything.
            // VI: Lọc theo danh mục mà không khớp sản phẩm nào thì phải trả về rỗng, không phải tất cả.
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("productId").in(productIds);
    }

    public static Specification<Auction> priceAtLeast(BigDecimal min) {
        if (min == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("currentPrice"), min);
    }

    public static Specification<Auction> priceAtMost(BigDecimal max) {
        if (max == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("currentPrice"), max);
    }

    /**
     * EN: Closing within the hour, and actually open — a lot that already ended is not "ending soon".
     * VI: Đóng trong vòng một giờ tới, và đang thật sự mở — lô đã kết thúc thì không phải "sắp đóng".
     */
    public static Specification<Auction> endingSoon(boolean endingSoon, Instant now, long withinSeconds) {
        if (!endingSoon) {
            return null;
        }
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), AuctionStatus.ACTIVE),
                cb.greaterThan(root.get("endTime"), now),
                cb.lessThanOrEqualTo(root.get("endTime"), now.plusSeconds(withinSeconds)));
    }
}
