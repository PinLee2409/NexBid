package com.nexbid.order;

/**
 * EN: Spec §5.3. PENDING_PAYMENT → PAID follows the payment; PAID → PROCESSING is the seller shipping,
 *     PROCESSING → COMPLETED the buyer confirming receipt; an admin refund cancels a PAID or PROCESSING order.
 * VI: Theo spec §5.3. PENDING_PAYMENT → PAID đi theo khoản thanh toán; PAID → PROCESSING là người bán gửi hàng,
 *     PROCESSING → COMPLETED là người mua xác nhận đã nhận; admin hoàn tiền thì huỷ đơn PAID hoặc PROCESSING.
 */
public enum OrderStatus {
    /** EN: Won, waiting for the winner to pay. / VI: Đã thắng, chờ người thắng thanh toán. */
    PENDING_PAYMENT,
    /** EN: Paid; waiting for the seller to ship. / VI: Đã trả; chờ người bán gửi hàng. */
    PAID,
    /** EN: Shipped; waiting for the buyer to confirm receipt. / VI: Đã gửi; chờ người mua xác nhận đã nhận. */
    PROCESSING,
    /** EN: Received by the buyer. Final. / VI: Người mua đã nhận hàng. Không đổi nữa. */
    COMPLETED,
    /** EN: Never paid, or refunded by an admin. Final. / VI: Không được thanh toán, hoặc đã được admin hoàn tiền. Không đổi nữa. */
    CANCELLED;

    /** EN: Money has changed hands and the goods have not been confirmed. / VI: Tiền đã trả và hàng chưa được xác nhận. */
    public boolean isRefundable() {
        return this == PAID || this == PROCESSING;
    }
}
