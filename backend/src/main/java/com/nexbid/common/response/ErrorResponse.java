package com.nexbid.common.response;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nexbid.common.exception.ErrorCode;

/**
 * EN: Failure envelope for every endpoint (spec §28).
 * VI: Khung phản hồi lỗi cho mọi endpoint (spec §28).
 *
 * @param success   always false / luôn là false
 * @param code      what went wrong, client translates it / lỗi gì, client tự dịch
 * @param message   English, for developers only / tiếng Anh, chỉ dành cho dev
 * @param timestamp when, in UTC / thời điểm, theo UTC
 * @param details   one entry per invalid field / mỗi field sai một dòng
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        boolean success,
        ErrorCode code,
        String message,
        Instant timestamp,
        Map<String, String> details) {

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(false, code, message, Instant.now(), null);
    }

    public static ErrorResponse of(ErrorCode code, String message, Map<String, String> details) {
        return new ErrorResponse(false, code, message, Instant.now(), details);
    }
}
