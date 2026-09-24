package com.nexbid.auction;

import java.util.List;
import java.util.UUID;

import com.nexbid.product.CategoryView;
import com.nexbid.product.ProductImageView;

/**
 * EN: The parts of a lot that bidding never touches — what is cached (guide §34). Price, bid count, status
 *     and the clock are never in here, so no bid can ever make a cached card wrong.
 * VI: Những phần của một lô mà việc trả giá không bao giờ đụng tới — thứ được cache (guide §34). Giá, số
 *     lượt, trạng thái và đồng hồ không bao giờ nằm ở đây, nên không lượt trả giá nào làm thẻ cache bị sai.
 */
record LotCard(
        UUID productId,
        String name,
        String description,
        String condition,
        CategoryView category,
        List<ProductImageView> images,
        UUID sellerId,
        String sellerName) {

    /** EN: The first image in display order, as the catalogue shows it. / VI: Ảnh đầu tiên theo thứ tự hiển thị, như danh mục hiện. */
    String coverImageUrl() {
        return images.isEmpty() ? null : images.getFirst().url();
    }
}
