package com.nexbid.auction.dto;

import java.math.BigDecimal;
import java.util.List;

import com.nexbid.auction.AuctionSort;
import com.nexbid.auction.AuctionStatus;

/**
 * EN: Everything the browse URL can carry (guide §18). All optional — a bare /api/auctions is valid.
 * VI: Mọi thứ URL duyệt hàng có thể mang theo (guide §18). Đều không bắt buộc — gọi trần /api/auctions vẫn hợp lệ.
 */
public record AuctionQuery(
        List<AuctionStatus> status,
        List<String> category,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean endingSoon,
        AuctionSort sort,
        Integer page,
        Integer size) {

    /** EN: Matches the frontend's own page size. / VI: Khớp với kích thước trang của frontend. */
    private static final int DEFAULT_SIZE = 12;
    private static final int MAX_SIZE = 50;

    public AuctionQuery {
        // EN: The URLs count pages from one; a cap stops a request asking for the whole table.
        // VI: URL đánh số trang từ 1; có trần để một request không đòi lấy cả bảng.
        page = page == null || page < 1 ? 1 : page;
        size = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        sort = sort == null ? AuctionSort.ENDING_SOON : sort;
    }

    /** EN: Spring counts from zero. / VI: Spring đánh số từ 0. */
    public int zeroBasedPage() {
        return page - 1;
    }
}
