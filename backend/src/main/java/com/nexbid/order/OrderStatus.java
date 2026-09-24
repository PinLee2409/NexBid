package com.nexbid.order;

/**
 * EN: Spec §5.3, minus PROCESSING and COMPLETED — the spec has no shipping step, so nothing could move an
 *     order into either. They arrive with the function that needs them.
 * VI: Theo spec §5.3, bỏ PROCESSING và COMPLETED — spec không có bước giao hàng, nên không gì đưa được đơn vào
 *     hai trạng thái đó. Chúng sẽ tới cùng chức năng cần tới chúng.
 */
public enum OrderStatus {
    /** EN: Won, waiting for the winner to pay. / VI: Đã thắng, chờ người thắng thanh toán. */
    PENDING_PAYMENT,
    /** EN: Paid; the sale is done. / VI: Đã trả; giao dịch hoàn tất. */
    PAID,
    /** EN: The payment window closed unpaid. / VI: Quá hạn thanh toán mà chưa trả. */
    CANCELLED
}
