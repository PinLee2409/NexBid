package com.nexbid.bid.dto;

/**
 * EN: What the bid-history URL can carry (guide §22). A popular lot collects thousands of bids and the
 *     page shows the top few, so the list is paged rather than returned whole.
 * VI: Những gì URL lịch sử trả giá có thể mang theo (guide §22). Một lô sôi động gom hàng nghìn lượt trong
 *     khi trang chỉ hiện vài dòng đầu, nên danh sách trả theo trang chứ không trả hết một lần.
 */
public record BidHistoryQuery(Integer page, Integer size) {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public BidHistoryQuery {
        page = page == null || page < 1 ? 1 : page;
        size = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    /** EN: Spring counts from zero. / VI: Spring đánh số từ 0. */
    public int zeroBasedPage() {
        return page - 1;
    }
}
