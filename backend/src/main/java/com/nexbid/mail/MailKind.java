package com.nexbid.mail;

/**
 * EN: The emails NexBid sends. Each has one bilingual template in {@link MailTemplates}.
 * VI: Các email NexBid gửi. Mỗi loại có một mẫu song ngữ trong {@link MailTemplates}.
 */
public enum MailKind {
    WELCOME,
    OUTBID,
    AUCTION_WON,
    /** EN: To the leader, when the lot closed below its reserve. / VI: Gửi người dẫn đầu, khi lô đóng dưới giá sàn. */
    RESERVE_PENDING,
    /** EN: To the leader, when the seller turned the bid down. / VI: Gửi người dẫn đầu, khi người bán từ chối giá. */
    RESERVE_REFUSED,
    PAYMENT_REMINDER,
    PAYMENT_RECEIPT,
    PAYMENT_EXPIRED,
    PAYMENT_REFUNDED,
    SECOND_CHANCE_OFFER,
    ORDER_SHIPPED,
    SELLER_APPROVED,
    SELLER_REJECTED,
    /** EN: To the seller: the lot has a buyer. / VI: Gửi người bán: lô đã có người mua. */
    LOT_SOLD,
    /** EN: To the seller: the buyer paid, ship it. / VI: Gửi người bán: người mua đã trả tiền, hãy gửi hàng. */
    SELLER_PAID,
    /** EN: To the seller: the best bid is below the reserve. / VI: Gửi người bán: giá cao nhất thấp hơn giá sàn. */
    RESERVE_DECISION,
    /** EN: To the seller, when the winner did not pay. / VI: Gửi người bán, khi người thắng không trả tiền. */
    SALE_CANCELLED,
    OFFER_ACCEPTED,
    OFFER_DECLINED,
    NEW_REVIEW,
    /** EN: To the seller: the buyer reported a problem. / VI: Gửi người bán: người mua báo có vấn đề. */
    DISPUTE_OPENED,
    /** EN: To the buyer: the dispute was turned down. / VI: Gửi người mua: khiếu nại bị bác. */
    DISPUTE_REJECTED,
    /** EN: To the seller: the buyer was refunded. / VI: Gửi người bán: người mua đã được hoàn tiền. */
    DISPUTE_SELLER_REFUNDED,
    /** EN: To the seller: the sale stands. / VI: Gửi người bán: giao dịch được giữ nguyên. */
    DISPUTE_SELLER_REJECTED,
    /** EN: To the seller: money was transferred. / VI: Gửi người bán: tiền đã được chuyển. */
    SELLER_PAYOUT,
    /** EN: To the buyer: the order completes on its own soon. / VI: Gửi người mua: đơn sắp tự hoàn tất. */
    ORDER_COMPLETING
}
