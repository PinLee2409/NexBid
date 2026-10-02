package com.nexbid.user;

import java.util.List;

/** EN: One page of the admin queue, in the shape of every other paged list. / VI: Một trang của hàng chờ admin, cùng dạng với mọi danh sách phân trang khác. */
public record SellerApplicationPage(
        List<SellerApplicationView> items, int page, int pageSize, long totalItems, int totalPages) {
}
