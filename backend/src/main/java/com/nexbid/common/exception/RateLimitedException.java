package com.nexbid.common.exception;

import java.time.Duration;

/**
 * EN: Too many requests (spec §20.2). Carries how long until the next one would be let through, which the
 *     response sends as Retry-After so the client can wait instead of guessing.
 * VI: Quá nhiều request (spec §20.2). Mang theo còn bao lâu nữa thì request kế tiếp được cho qua, response
 *     gửi nó thành Retry-After để client chờ đúng thay vì đoán.
 */
public class RateLimitedException extends BusinessException {

    private final Duration retryAfter;

    public RateLimitedException(ErrorCode code, String message, Duration retryAfter) {
        super(code, message);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
