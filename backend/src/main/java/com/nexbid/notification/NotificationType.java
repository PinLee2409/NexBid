package com.nexbid.notification;

/**
 * EN: Spec §18's types, each added with the function that causes it.
 * VI: Các loại của spec §18, mỗi loại thêm vào cùng chức năng gây ra nó.
 */
public enum NotificationType {
    /** EN: Someone bid higher than you. / VI: Có người trả cao hơn bạn. */
    OUTBID,
    /** EN: You won a lot; it says what to pay (spec §38). / VI: Bạn đã thắng một lô; kèm số tiền phải trả (spec §38). */
    AUCTION_WON,
    /** EN: A lot you bid on went to someone else. / VI: Lô bạn đã trả giá thuộc về người khác. */
    AUCTION_LOST,
    /** EN: A lot you watch opens soon. / VI: Lô bạn theo dõi sắp mở. */
    AUCTION_STARTING,
    /** EN: A lot you watch closes soon. / VI: Lô bạn theo dõi sắp đóng. */
    AUCTION_ENDING,
    /**
     * EN: A last-minute bid pushed back the close of a lot you bid on or watch. Once per lot until read.
     * VI: Một lượt trả giá phút chót đã lùi giờ đóng của lô bạn trả giá hoặc theo dõi. Mỗi lô một lần tới khi đọc.
     */
    AUCTION_EXTENDED,
    /** EN: Reminder: the payment for a lot you won is due soon. / VI: Nhắc: sắp tới hạn thanh toán lô bạn đã thắng. */
    PAYMENT_REQUIRED,
    /** EN: Your payment went through. / VI: Bạn đã thanh toán thành công. */
    PAYMENT_SUCCESS,
    /** EN: You did not pay in time; the lot was cancelled. / VI: Bạn không thanh toán kịp; lô đã bị huỷ. */
    PAYMENT_EXPIRED,
    /** EN: A sale fell through unpaid — told to the seller and the winner. / VI: Giao dịch đổ vì không thanh toán — báo người bán và người thắng. */
    AUCTION_CANCELLED
}
