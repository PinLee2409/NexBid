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

    /* --- Auction lifecycle / Vòng đời phiên -------------------------- */
    AUCTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    AUCTION_NOT_ACTIVE(HttpStatus.CONFLICT),
    AUCTION_ALREADY_ENDED(HttpStatus.CONFLICT),
    AUCTION_NOT_EDITABLE(HttpStatus.CONFLICT),

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

    /* --- Settlement / Thanh toán -------------------------------------- */
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    PAYMENT_EXPIRED(HttpStatus.CONFLICT),

    /* --- Access / Quyền truy cập -------------------------------------- */
    ACCESS_DENIED(HttpStatus.FORBIDDEN),

    // EN: §29 names ACCESS_DENIED for 403 but nothing for 401 — and they mean different things.
    // VI: §29 có ACCESS_DENIED cho 403 nhưng bỏ trống 401 — hai cái mang ý nghĩa khác nhau.
    NOT_AUTHENTICATED(HttpStatus.UNAUTHORIZED),
    // EN: Used by name in the guide's §4 example.
    // VI: Guide §4 gọi đích danh mã này trong ví dụ.
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
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
