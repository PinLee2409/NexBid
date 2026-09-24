package com.nexbid.notification.dto;

/**
 * EN: Paging for the bell's list. Notices only accumulate, so the list is never returned whole.
 * VI: Phân trang cho danh sách của cái chuông. Thông báo chỉ tích luỹ thêm, nên không bao giờ trả cả danh sách.
 */
public record NotificationQuery(Integer page, Integer size) {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public NotificationQuery {
        page = page == null || page < 1 ? 1 : page;
        size = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    /** EN: Spring counts from zero. / VI: Spring đánh số từ 0. */
    public int zeroBasedPage() {
        return page - 1;
    }
}
