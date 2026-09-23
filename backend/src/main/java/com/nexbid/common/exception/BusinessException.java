package com.nexbid.common.exception;

/**
 * EN: A domain rule refused the request. Carries an ErrorCode, never an HTTP status.
 * VI: Nghiệp vụ từ chối yêu cầu. Mang ErrorCode, không bao giờ mang HTTP status.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode code) {
        this(code, code.name());
    }

    public ErrorCode code() {
        return code;
    }
}
