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
    // EN: Admins cannot block an admin — not another one, not themselves. / VI: Admin không khoá được admin — kể cả chính mình.
    ADMIN_NOT_BLOCKABLE(HttpStatus.CONFLICT),
    STAFF_ROLES_INVALID(HttpStatus.BAD_REQUEST),
    DEPOSIT_REQUIRED(HttpStatus.PAYMENT_REQUIRED),
    DEPOSIT_NOT_NEEDED(HttpStatus.CONFLICT),
    DEPOSIT_ALREADY_HELD(HttpStatus.CONFLICT),
    DEPOSIT_NOT_FOUND(HttpStatus.NOT_FOUND),
    DEPOSIT_NOT_DUE(HttpStatus.CONFLICT),
    OFFER_UNAVAILABLE(HttpStatus.CONFLICT),
    OFFER_INVALID(HttpStatus.BAD_REQUEST),
    OFFER_LIMIT(HttpStatus.CONFLICT),
    RELIST_NOT_ALLOWED(HttpStatus.CONFLICT),
    VACATION_INVALID(HttpStatus.BAD_REQUEST),
    SELLER_ON_VACATION(HttpStatus.CONFLICT),
    TEMPLATE_NOT_FOUND(HttpStatus.NOT_FOUND),
    TEMPLATE_INVALID(HttpStatus.BAD_REQUEST),
    COUPON_INVALID(HttpStatus.BAD_REQUEST),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND),
    QUICK_REPLY_LIMIT(HttpStatus.CONFLICT),
    QUICK_REPLY_NOT_FOUND(HttpStatus.NOT_FOUND),
    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND),
    SEALED_BID_EXISTS(HttpStatus.CONFLICT),
    SEALED_LOT(HttpStatus.CONFLICT),
    // EN: Missing, expired, signed out, or replayed after it was replaced. / VI: Thiếu, hết hạn, đã đăng xuất, hoặc bị dùng lại sau khi đã được thay.
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    // EN: Too many wrong passwords; Retry-After says when to try again. / VI: Sai mật khẩu quá nhiều lần; Retry-After cho biết khi nào thử lại.
    LOGIN_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    // EN: Becoming a seller (spec §7.1): asking twice, asking when already one, deciding twice.
    // VI: Trở thành người bán (spec §7.1): gửi hai lần, gửi khi đã là người bán, quyết định hai lần.
    ALREADY_SELLER(HttpStatus.CONFLICT),
    SELLER_APPLICATION_PENDING(HttpStatus.CONFLICT),
    SELLER_APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    SELLER_APPLICATION_NOT_PENDING(HttpStatus.CONFLICT),

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
    // EN: Reserve below the starting price, or a buy-now price below either. / VI: Giá sàn thấp hơn giá khởi điểm, hoặc giá mua ngay thấp hơn một trong hai.
    AUCTION_PRICES_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
    // EN: The lot is not waiting for the seller to accept a bid below its reserve. / VI: Lô không chờ người bán nhận một giá dưới giá sàn.
    RESERVE_DECISION_CLOSED(HttpStatus.CONFLICT),

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
    // EN: No buy-now price, or someone has already bid. / VI: Không có giá mua ngay, hoặc đã có người trả giá.
    BUY_NOW_UNAVAILABLE(HttpStatus.CONFLICT),

    /* --- Settlement / Thanh toán -------------------------------------- */
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    PAYMENT_EXPIRED(HttpStatus.CONFLICT),
    // EN: Already settled — paying twice would charge twice. / VI: Đã thanh toán rồi — trả lần nữa là trừ tiền hai lần.
    PAYMENT_ALREADY_PAID(HttpStatus.CONFLICT),
    // EN: A VNPay callback whose signature or amount does not check out. / VI: Lời gọi lại từ VNPay có chữ ký hoặc số tiền không khớp.
    PAYMENT_GATEWAY_INVALID(HttpStatus.BAD_REQUEST),
    // EN: Checkout needs somewhere to deliver to. / VI: Checkout cần có nơi giao hàng.
    ADDRESS_REQUIRED(HttpStatus.CONFLICT),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    // EN: A step out of order, e.g. confirming receipt of an order never shipped. / VI: Sai thứ tự bước, ví dụ xác nhận đã nhận một đơn chưa gửi.
    ORDER_STATUS_INVALID(HttpStatus.CONFLICT),
    // EN: A refund is a bank transfer: the buyer must have said where to. / VI: Hoàn tiền là chuyển khoản: người mua phải cho biết chuyển về đâu.
    REFUND_ACCOUNT_REQUIRED(HttpStatus.CONFLICT),
    // EN: Money VNPay took that no payment could use: none such, or already sent back.
    // VI: Tiền VNPay đã thu mà không khoản nào dùng được: không có khoản này, hoặc đã trả lại rồi.
    STRAY_CHARGE_NOT_FOUND(HttpStatus.NOT_FOUND),
    STRAY_CHARGE_REFUNDED(HttpStatus.CONFLICT),
    // EN: The seller's balance changed after the admin read it. / VI: Số dư của người bán đã đổi sau khi admin xem.
    PAYOUT_AMOUNT_CHANGED(HttpStatus.CONFLICT),
    // EN: The email outbox: no such email, or one that has not failed. / VI: Hàng chờ email: không có email này, hoặc email chưa hỏng.
    EMAIL_NOT_FOUND(HttpStatus.NOT_FOUND),
    EMAIL_NOT_RETRYABLE(HttpStatus.CONFLICT),
    // EN: A bank reference already recorded for another transfer. / VI: Mã giao dịch ngân hàng đã được ghi cho lần chuyển khác.
    REFERENCE_USED(HttpStatus.CONFLICT),
    // EN: Only a scheduled or live lot can be taken down. / VI: Chỉ gỡ được lô đang chờ mở hoặc đang mở.
    AUCTION_NOT_REMOVABLE(HttpStatus.CONFLICT),
    // EN: Lot reports: none such, already decided, not allowed (own or closed lot), already reported, too many waiting.
    // VI: Báo cáo lô: không có, đã xử lý, không được phép (lô của mình hoặc đã đóng), đã báo rồi, quá nhiều báo cáo chờ.
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND),
    REPORT_CLOSED(HttpStatus.CONFLICT),
    REPORT_NOT_ALLOWED(HttpStatus.CONFLICT),
    REPORT_DUPLICATE(HttpStatus.CONFLICT),
    REPORT_LIMIT(HttpStatus.TOO_MANY_REQUESTS),
    // EN: Buyer reviews and the unpaid-win limit. / VI: Đánh giá người mua và giới hạn với người hay bỏ không trả.
    BUYER_REVIEW_NOT_ALLOWED(HttpStatus.CONFLICT),
    BIDDER_RESTRICTED(HttpStatus.FORBIDDEN),
    // EN: Only a seller carries the verified badge. / VI: Chỉ người bán mới mang huy hiệu xác minh.
    NOT_A_SELLER(HttpStatus.CONFLICT),
    // EN: Order messages: closed (cancelled, or long completed), contact details refused, too many at once.
    // VI: Tin nhắn đơn hàng: đã đóng (đơn huỷ hoặc hoàn tất lâu rồi), chứa thông tin liên lạc, gửi quá nhiều.
    MESSAGES_CLOSED(HttpStatus.CONFLICT),
    MESSAGE_BLOCKED(HttpStatus.UNPROCESSABLE_ENTITY),
    MESSAGE_LIMIT(HttpStatus.TOO_MANY_REQUESTS),
    // EN: GHN is not connected, or did not answer. / VI: Chưa kết nối GHN, hoặc GHN không trả lời.
    SHIPPING_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    // EN: GHN has no order with the code the seller typed. / VI: GHN không có đơn nào mang mã người bán đã nhập.
    TRACKING_NOT_FOUND(HttpStatus.UNPROCESSABLE_ENTITY),
    // EN: GHN would not take the parcel (its message is kept), or an address lacks GHN ids. / VI: GHN không nhận kiện (giữ lời của GHN), hoặc một địa chỉ thiếu mã GHN.
    GHN_REFUSED(HttpStatus.UNPROCESSABLE_ENTITY),
    GHN_ADDRESS_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY),
    // EN: Two-step sign-in: already on, setup not started, a wrong code, or the code step came too late.
    // VI: Đăng nhập hai bước: đã bật, chưa bắt đầu cài đặt, mã sai, hoặc bước nhập mã đến quá muộn.
    TWO_FACTOR_ALREADY_ON(HttpStatus.CONFLICT),
    TWO_FACTOR_NOT_SET_UP(HttpStatus.CONFLICT),
    TWO_FACTOR_CODE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
    TWO_FACTOR_CHALLENGE_EXPIRED(HttpStatus.UNAUTHORIZED),
    // EN: Google sign-in is not set up, or Google's token did not check out. / VI: Chưa cài đăng nhập Google, hoặc token của Google không hợp lệ.
    GOOGLE_SIGN_IN_OFF(HttpStatus.SERVICE_UNAVAILABLE),
    GOOGLE_SIGN_IN_FAILED(HttpStatus.UNAUTHORIZED),
    // EN: A CSV of lots that cannot be read at all (rows with problems are reported one by one instead).
    // VI: File CSV lô không đọc được (dòng có lỗi thì được báo từng dòng).
    IMPORT_INVALID(HttpStatus.BAD_REQUEST),
    PUSH_SUBSCRIPTION_INVALID(HttpStatus.BAD_REQUEST),
    PROMO_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
    PROMO_EXISTS(HttpStatus.CONFLICT),
    PASSWORD_RESET_INVALID(HttpStatus.UNPROCESSABLE_ENTITY),
    PASSWORD_INCORRECT(HttpStatus.UNPROCESSABLE_ENTITY),
    PASSWORD_UNCHANGED(HttpStatus.UNPROCESSABLE_ENTITY),
    VIDEO_INVALID(HttpStatus.BAD_REQUEST),
    // EN: Questions on a lot: none such, closed lot or own lot, too many waiting, or already answered.
    // VI: Hỏi đáp trên lô: không có câu hỏi, lô đã đóng hoặc lô của chính mình, quá nhiều câu đang chờ, hoặc đã trả lời.
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    QUESTION_NOT_ALLOWED(HttpStatus.CONFLICT),
    QUESTION_LIMIT(HttpStatus.TOO_MANY_REQUESTS),
    QUESTION_ANSWERED(HttpStatus.CONFLICT),
    // EN: Lot alerts: no such saved search, too many saved, or following someone who cannot be followed.
    // VI: Báo lô mới: không có bộ lọc đã lưu này, lưu quá nhiều, hoặc theo dõi người không theo dõi được.
    SAVED_SEARCH_NOT_FOUND(HttpStatus.NOT_FOUND),
    SAVED_SEARCH_LIMIT(HttpStatus.CONFLICT),
    FOLLOW_NOT_ALLOWED(HttpStatus.CONFLICT),
    DISPUTE_NOT_FOUND(HttpStatus.NOT_FOUND),
    // EN: Already decided. / VI: Đã được quyết định rồi.
    DISPUTE_CLOSED(HttpStatus.CONFLICT),
    // EN: A payout needs somewhere to send the money. / VI: Chi trả cần có nơi để chuyển tiền.
    BANK_ACCOUNT_REQUIRED(HttpStatus.CONFLICT),
    // EN: The seller has no completed sale waiting to be paid. / VI: Người bán không có đơn hoàn tất nào đang chờ chi trả.
    NOTHING_TO_PAY_OUT(HttpStatus.CONFLICT),
    // EN: Second-chance offers (spec §17): the lot cannot be offered, nobody to offer it to, or offered already.
    // VI: Đề nghị cơ hội thứ hai (spec §17): lô không đề nghị được, không có ai để đề nghị, hoặc đã đề nghị rồi.
    SECOND_CHANCE_UNAVAILABLE(HttpStatus.CONFLICT),
    NO_RUNNER_UP(HttpStatus.CONFLICT),
    OFFER_ALREADY_MADE(HttpStatus.CONFLICT),
    OFFER_NOT_FOUND(HttpStatus.NOT_FOUND),
    // EN: Already accepted, declined, or past its 24 hours. / VI: Đã nhận, đã từ chối, hoặc đã quá 24 giờ.
    OFFER_NOT_PENDING(HttpStatus.CONFLICT),
    // EN: Reviews: only a received order, and only once. / VI: Đánh giá: chỉ đơn đã nhận hàng, và chỉ một lần.
    REVIEW_NOT_ALLOWED(HttpStatus.CONFLICT),
    REVIEW_EXISTS(HttpStatus.CONFLICT),

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
