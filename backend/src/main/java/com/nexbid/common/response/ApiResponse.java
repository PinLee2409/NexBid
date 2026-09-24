package com.nexbid.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * EN: Success envelope for every endpoint (spec §28). A record, so the shape cannot be changed.
 * VI: Khung phản hồi thành công cho mọi endpoint (spec §28). Dùng record để không ai sửa được shape.
 *
 * @param success always true / luôn là true
 * @param message short note for developers and logs / ghi chú ngắn cho dev và log
 * @param data    the payload, omitted when empty / dữ liệu, bỏ hẳn khi rỗng
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> of(T data, String message) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(true, null, data);
    }

    /**
     * EN: For endpoints that change something and return nothing.
     * VI: Dành cho endpoint có thay đổi dữ liệu nhưng không trả về gì.
     */
    public static ApiResponse<Void> ok(String message) {
        return new ApiResponse<>(true, message, null);
    }
}
