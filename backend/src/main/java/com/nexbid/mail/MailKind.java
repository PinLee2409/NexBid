package com.nexbid.mail;

/**
 * EN: The emails NexBid sends. Each has one bilingual template in {@link MailTemplates}.
 * VI: Các email NexBid gửi. Mỗi loại có một mẫu song ngữ trong {@link MailTemplates}.
 */
public enum MailKind {
    WELCOME,
    OUTBID,
    AUCTION_WON,
    PAYMENT_REMINDER,
    PAYMENT_RECEIPT,
    PAYMENT_EXPIRED,
    /** EN: To the seller, when the winner did not pay. / VI: Gửi người bán, khi người thắng không trả tiền. */
    SALE_CANCELLED,
    SECOND_CHANCE_OFFER,
    SELLER_APPROVED,
    SELLER_REJECTED,
    ORDER_SHIPPED
}
