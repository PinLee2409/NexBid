package com.nexbid.audit;

/** EN: Stable action names in the audit API. / VI: Tên hành động cố định trong API audit. */
public enum AuditAction {
    USER_LOGIN,
    AUCTION_CREATED,
    AUCTION_APPROVED,
    AUCTION_REJECTED,
    BID_PLACED,
    AUCTION_EXTENDED,
    AUCTION_ENDED,
    PAYMENT_SUCCESS,
    PAYMENT_REFUNDED,
    ORDER_SHIPPED,
    ORDER_COMPLETED,
    USER_BLOCKED,
    USER_UNBLOCKED
}
