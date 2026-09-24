package com.nexbid.auction;

/**
 * EN: The life of an auction (spec §5.1). The happy path runs top to bottom; the last two are exits.
 * VI: Vòng đời một phiên đấu giá (spec §5.1). Đường thuận chạy từ trên xuống; hai cái cuối là lối thoát.
 */
public enum AuctionStatus {
    /** EN: The seller is still filling it in. / VI: Người bán còn đang nhập. */
    DRAFT,
    /** EN: Sent to an admin. / VI: Đã gửi cho admin. */
    PENDING_APPROVAL,
    /** EN: Approved, waiting for its start time. / VI: Đã duyệt, chờ tới giờ mở. */
    SCHEDULED,
    /** EN: Open for bidding. / VI: Đang nhận trả giá. */ 
    ACTIVE,
    /** EN: The clock ran out. / VI: Đã hết giờ. */
    ENDED,
    /** EN: The winner paid. / VI: Người thắng đã thanh toán. */
    COMPLETED,
    /** EN: An admin said no. / VI: Admin từ chối. */
    REJECTED,
    /** EN: Called off. / VI: Bị huỷ. */
    CANCELLED;

    /**
     * EN: Only a draft is the seller's to change (spec §7.4) — once it is sent for approval it is not.
     * VI: Chỉ bản nháp mới thuộc quyền sửa của người bán (spec §7.4) — gửi duyệt rồi là hết.
     */
    public boolean isEditableBySeller() {
        return this == DRAFT;
    }

    /** EN: Still occupying its product. / VI: Vẫn đang chiếm giữ sản phẩm của nó. */
    public boolean holdsTheProduct() {
        return this == DRAFT || this == PENDING_APPROVAL || this == SCHEDULED
                || this == ACTIVE || this == ENDED;
    }
}
