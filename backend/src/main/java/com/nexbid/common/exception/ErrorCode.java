package com.nexbid.common.exception;

import org.springframework.http.HttpStatus;

/**
 * EN: Every failure the API can report (spec §29). The code is the contract — clients translate it.
 * VI: Mọi lỗi API có thể trả (spec §29). Mã lỗi mới là hợp đồng — client tự dịch sang ngôn ngữ của họ.
 */
public enum ErrorCode {

    /* --- Identity / Tài khoản ---------------------------------------- */
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCOUNT_BLOCKED(HttpStatus.FORBIDDEN),

    /* --- Catalogue / Sản phẩm ---------------------------------------- */
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
    // EN: Not in §29, which never mentions categories — they arrive with guide §10.
    // VI: Không có trong §29 vì đặc tả không nhắc tới danh mục — chúng xuất hiện ở guide §10.
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND),
    CATEGORY_ALREADY_EXISTS(HttpStatus.CONFLICT),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND),
    // EN: Not an image, or unreadable — the request is well formed, the content is not.
    // VI: Không phải ảnh, hoặc không đọc được — request đúng dạng nhưng nội dung thì không.
    IMAGE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
    IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    IMAGE_LIMIT_REACHED(HttpStatus.CONFLICT),
    // EN: The product is in an auction or already sold, so it is no longer the seller's to change.
    // VI: Sản phẩm đang trong phiên đấu giá hoặc đã bán, nên không còn thuộc quyền sửa của người bán.
    PRODUCT_NOT_EDITABLE(HttpStatus.CONFLICT),
    // EN: The product is a draft or already sold, so it has no business being on the block.
    // VI: Sản phẩm còn là nháp hoặc đã bán, nên không có lý gì đưa lên sàn.
    PRODUCT_NOT_SELLABLE(HttpStatus.CONFLICT),
    PRODUCT_ALREADY_IN_AUCTION(HttpStatus.CONFLICT),

    /* --- Auction lifecycle / Vòng đời phiên -------------------------- */
    AUCTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    AUCTION_NOT_ACTIVE(HttpStatus.CONFLICT),
    AUCTION_ALREADY_ENDED(HttpStatus.CONFLICT),
    AUCTION_NOT_EDITABLE(HttpStatus.CONFLICT),
    // EN: The start and end times cannot make a runnable auction.
    // VI: Mốc bắt đầu và kết thúc không tạo ra được một phiên chạy được.
    AUCTION_SCHEDULE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
    // EN: A decision was made on a lot that is not waiting for one.
    // VI: Có người ra quyết định trên một lô vốn không chờ quyết định.
    AUCTION_NOT_PENDING(HttpStatus.CONFLICT),

    /* --- Bidding / Trả giá -------------------------------------------- */
    SELLER_CANNOT_BID(HttpStatus.FORBIDDEN),
    // EN: Request is valid, the amount just is not enough — 422, not 400.
    // VI: Request hợp lệ, chỉ là số tiền chưa đủ — dùng 422, không phải 400.
    BID_TOO_LOW(HttpStatus.UNPROCESSABLE_ENTITY),
    // EN: Someone else's bid landed first (spec §9); the client must re-read and retry.
    // VI: Bid của người khác vào trước (spec §9); client phải đọc lại giá rồi thử lại.
    BID_CONFLICT(HttpStatus.CONFLICT),
    BID_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    AUTO_BID_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
    // EN: Spec §14 allows one active auto bid per person per lot; change it with PUT instead.
    // VI: Spec §14 chỉ cho mỗi người một auto bid đang bật trên mỗi lô; muốn đổi thì dùng PUT.
    AUTO_BID_EXISTS(HttpStatus.CONFLICT),
    AUTO_BID_NOT_FOUND(HttpStatus.NOT_FOUND),

    /* --- Settlement / Thanh toán -------------------------------------- */
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    PAYMENT_EXPIRED(HttpStatus.CONFLICT),
    // EN: Already settled — paying twice would charge twice. / VI: Đã thanh toán rồi — trả lần nữa là trừ tiền hai lần.
    PAYMENT_ALREADY_PAID(HttpStatus.CONFLICT),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),

    /* --- Notifications / Thông báo ------------------------------------ */
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND),

    /* --- Access / Quyền truy cập -------------------------------------- */
    ACCESS_DENIED(HttpStatus.FORBIDDEN),

    // EN: §29 names ACCESS_DENIED for 403 but nothing for 401 — and they mean different things.
    // VI: §29 có ACCESS_DENIED cho 403 nhưng bỏ trống 401 — hai cái mang ý nghĩa khác nhau.
    NOT_AUTHENTICATED(HttpStatus.UNAUTHORIZED),
    // EN: Used by name in the guide's §4 example.
    // VI: Guide §4 gọi đích danh mã này trong ví dụ.
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    // EN: No such route. Distinct from USER_NOT_FOUND and friends, which mean a record is missing.
    // VI: Không có route này. Khác với USER_NOT_FOUND và tương tự, vốn nghĩa là thiếu bản ghi.
    NOT_FOUND(HttpStatus.NOT_FOUND),
    // EN: Right URL, wrong verb — a GET where the endpoint expects POST.
    // VI: Đúng URL nhưng sai method — gọi GET trong khi endpoint cần POST.
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    // EN: A body the endpoint cannot read, usually a missing Content-Type.
    // VI: Body endpoint không đọc được, thường do thiếu Content-Type.
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    // EN: The catch-all still has to answer in the documented shape.
    // VI: Catch-all vẫn phải trả về đúng shape đã quy định.
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    /**
     * EN: The status travels with the code, so one rule decides it everywhere.
     * VI: Status đi kèm mã lỗi, nên chỉ một nơi quyết định — không chỗ nào tự chọn khác.
     */
    public HttpStatus status() {
        return status;
    }
}
