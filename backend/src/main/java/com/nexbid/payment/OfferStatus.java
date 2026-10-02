package com.nexbid.payment;

/**
 * EN: Where a second-chance offer stands. Only PENDING can still change.
 * VI: Đề nghị cơ hội thứ hai đang ở đâu. Chỉ PENDING là còn thay đổi được.
 */
public enum OfferStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED
}
