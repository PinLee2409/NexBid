package com.nexbid.payment;

/**
 * EN: Spec §5.2.
 * VI: Theo spec §5.2.
 */
public enum PaymentStatus {
    /** EN: Waiting for the winner. / VI: Đang chờ người thắng. */
    PENDING,
    /** EN: Paid. Only an admin refund changes it. / VI: Đã trả. Chỉ admin hoàn tiền mới đổi được. */
    SUCCESS,
    /** EN: An attempt failed; the winner may try again until the deadline. / VI: Một lần thử hỏng; người thắng được thử lại tới hạn chót. */
    FAILED,
    /** EN: The deadline passed unpaid. Final. / VI: Quá hạn mà chưa trả. Không đổi nữa. */
    EXPIRED,
    /** EN: Paid, then given back by an admin. Final. / VI: Đã trả, rồi được admin hoàn lại. Không đổi nữa. */
    REFUNDED;

    /** EN: Still waiting on the winner. / VI: Vẫn đang chờ người thắng. */
    public boolean isOpen() {
        return this == PENDING || this == FAILED;
    }
}
