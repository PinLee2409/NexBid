package com.nexbid.order;

import java.math.BigDecimal;

import com.nexbid.auction.PageView;

/**
 * EN: A seller's standing: average stars (one decimal, null before the first review), how many reviews and sales,
 *     and a page of reviews, newest first. Sales count every order paid for, reviewed or not.
 * VI: Uy tín của người bán: số sao trung bình (một chữ số thập phân, null khi chưa có đánh giá), số đánh giá và số lượt
 *     bán, kèm một trang đánh giá mới nhất trước. Lượt bán tính mọi đơn đã trả tiền, có đánh giá hay không.
 */
public record SellerReviewsView(BigDecimal average, long reviewCount, long sales, PageView<ReviewView> reviews) {
}
