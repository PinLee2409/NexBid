package com.nexbid.notification;

/**
 * EN: Spec §18's types, each added with the function that causes it. An extension is announced on the lot's
 *     live channel instead (guide §30); "payment required" is the AUCTION_WON notice itself (spec §38).
 * VI: Các loại của spec §18, mỗi loại thêm vào cùng chức năng gây ra nó. Gia hạn thì loan báo trên kênh
 *     realtime của lô (guide §30); "cần thanh toán" chính là thông báo AUCTION_WON (spec §38).
 */
public enum NotificationType {
    /** EN: Someone bid higher than you. / VI: Có người trả cao hơn bạn. */
    OUTBID,
    /** EN: You won a lot. / VI: Bạn đã thắng một lô. */
    AUCTION_WON,
    /** EN: A lot you bid on went to someone else. / VI: Lô bạn đã trả giá thuộc về người khác. */
    AUCTION_LOST,
    /** EN: A lot you watch opens soon. / VI: Lô bạn theo dõi sắp mở. */
    AUCTION_STARTING,
    /** EN: A lot you watch closes soon. / VI: Lô bạn theo dõi sắp đóng. */
    AUCTION_ENDING,
    /** EN: Your payment went through. / VI: Bạn đã thanh toán thành công. */
    PAYMENT_SUCCESS,
    /** EN: You did not pay in time; the lot was cancelled. / VI: Bạn không thanh toán kịp; lô đã bị huỷ. */
    PAYMENT_EXPIRED
}
