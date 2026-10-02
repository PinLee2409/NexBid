package com.nexbid.user;

/**
 * EN: Where a request to become a seller stands. Only PENDING can still change.
 * VI: Yêu cầu trở thành người bán đang ở đâu. Chỉ PENDING là còn thay đổi được.
 */
public enum SellerApplicationStatus {
    PENDING,
    APPROVED,
    REJECTED
}
