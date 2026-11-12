package com.nexbid.auction;

/**
 * EN: What became of a lot that closed below its reserve: the seller has a window to accept the highest bid.
 * VI: Chuyện gì xảy ra với lô đóng dưới giá sàn: người bán có một khoảng thời gian để nhận giá cao nhất.
 */
public enum ReserveDecision {
    /** EN: Waiting for the seller. / VI: Đang chờ người bán. */
    PENDING,
    /** EN: Sold to the highest bidder after all. / VI: Rốt cuộc vẫn bán cho người trả cao nhất. */
    ACCEPTED,
    /** EN: The seller kept it. / VI: Người bán giữ lại. */
    DECLINED,
    /** EN: The seller did not answer in time; not sold. / VI: Người bán không trả lời kịp; không bán. */
    LAPSED
}
