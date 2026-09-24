package com.nexbid.auction;

import java.util.UUID;

import com.nexbid.product.CategoryView;

/**
 * EN: One card in the browse grid (guide §18): image, name, current price, bid count, clock, status.
 * VI: Một thẻ trong lưới duyệt hàng (guide §18): ảnh, tên, giá hiện tại, số lượt, đồng hồ, trạng thái.
 */
public record AuctionSummaryView(
        AuctionView auction,
        Product product,
        CategoryView category,
        Seller seller) {

    /** EN: Just enough of the item to draw a card. / VI: Vừa đủ thông tin món hàng để vẽ một thẻ. */
    public record Product(UUID id, String name, String coverImageUrl) {
    }

    public record Seller(UUID id, String displayName) {
    }
}
