package com.nexbid.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nexbid.auction.AuctionSummaryView;

/**
 * EN: A payment as its owner sees it, with the lot behind it. Field names follow the frontend's Payment type.
 * VI: Một khoản thanh toán dưới góc nhìn chủ nhân, kèm lô đứng sau nó. Tên trường theo kiểu Payment của frontend.
 */
public record PaymentView(Details payment, AuctionSummaryView auction) {

    public record Details(
            UUID id,
            UUID auctionId,
            UUID userId,
            BigDecimal amount,
            PaymentStatus status,
            Instant expiredAt,
            Instant createdAt,
            Instant updatedAt,
            // EN: Null until quoted at checkout; source is GHN or FLAT. / VI: Null tới khi báo phí lúc thanh toán; source là GHN hoặc FLAT.
            BigDecimal shippingFee,
            String shippingSource,
            BigDecimal total,
            // EN: Taken off the shipping by a voucher. / VI: Phần voucher trừ vào phí ship.
            BigDecimal shippingDiscount,
            String promoCode,
            // EN: Taken off the goods by the seller's own code. / VI: Phần mã của người bán trừ vào tiền hàng.
            BigDecimal sellerDiscount,
            String sellerCoupon,
            // EN: The lot whose parcel this one travels in, when shipping was combined. / VI: Lô mà kiện của nó chở luôn lô này, khi gộp ship.
            UUID shipsWithAuctionId) {
    }
}
