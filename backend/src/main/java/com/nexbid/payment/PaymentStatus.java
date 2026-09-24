package com.nexbid.payment;

/**
 * EN: Spec §5.2, minus REFUNDED — nothing in the spec ever refunds, so no code path could reach it.
 * VI: Theo spec §5.2, bỏ REFUNDED — spec không có luồng hoàn tiền nào, nên không đường code nào tới được nó.
 */
public enum PaymentStatus {
    /** EN: Waiting for the winner. / VI: Đang chờ người thắng. */
    PENDING,
    /** EN: Paid. Final. / VI: Đã trả. Không đổi nữa. */
    SUCCESS,
    /** EN: An attempt failed; the winner may try again until the deadline. / VI: Một lần thử hỏng; người thắng được thử lại tới hạn chót. */
    FAILED,
    /** EN: The deadline passed unpaid. Final. / VI: Quá hạn mà chưa trả. Không đổi nữa. */
    EXPIRED;

    /** EN: Still waiting on the winner. / VI: Vẫn đang chờ người thắng. */
    public boolean isOpen() {
        return this == PENDING || this == FAILED;
    }
}
