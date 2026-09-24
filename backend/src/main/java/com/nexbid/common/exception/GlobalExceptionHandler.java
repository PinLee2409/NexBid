package com.nexbid.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.nexbid.common.response.ErrorResponse;

/**
 * EN: Turns every failure into the shape in spec §28, so the client needs one parser, not one per route.
 * VI: Quy mọi lỗi về đúng shape spec §28, để client chỉ cần một bộ parse thay vì mỗi route một kiểu.
 *
 * <p>EN: 401 and 403 are shaped in SecurityConfig instead — filter-chain errors never reach a controller advice.
 * <p>VI: 401 và 403 nằm ở SecurityConfig — lỗi trong filter chain không bao giờ tới được controller advice.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * EN: Bean validation — report every bad field at once, not one per round trip.
     * VI: Bean validation — báo hết field sai trong một lần, không bắt user sửa từng cái.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // EN: LinkedHashMap keeps the field order the user sees on the form.
        // VI: LinkedHashMap giữ đúng thứ tự field như trên form người dùng đang nhìn.
        Map<String, String> details = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(
                    error.getField(),
                    error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage());
        }

        return respond(ErrorCode.VALIDATION_ERROR, "Request validation failed", details);
    }

    /**
     * EN: Malformed body, missing parameter, or a letter where a number belongs.
     * VI: Body sai định dạng, thiếu tham số, hoặc truyền chữ vào chỗ cần số.
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex) {
        log.debug("Malformed request: {}", ex.getMessage());
        return respond(ErrorCode.VALIDATION_ERROR, "Request could not be read", null);
    }

    /**
     * EN: Any domain refusal. The status comes from the code, so a new rule cannot invent a new status.
     * VI: Mọi từ chối từ nghiệp vụ. Status lấy từ mã lỗi, nên thêm luật mới không đẻ ra status mới.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        log.debug("Business rule refused: {} — {}", ex.code(), ex.getMessage());
        return respond(ex.code(), ex.getMessage(), null);
    }

    /**
     * EN: Method-level security, i.e. a denial decided after the filter chain.
     * VI: Phân quyền ở tầng method, tức là từ chối sau khi đã qua filter chain.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return respond(ErrorCode.ACCESS_DENIED, "You may not perform this action", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return respond(ErrorCode.NOT_AUTHENTICATED, "Authentication is required", null);
    }

    /**
     * EN: Spring's protocol failures — wrong URL, wrong verb, unreadable body. They already carry the right
     *     status, so this only translates it into our code. Without them the catch-all reports a typo as a bug.
     * VI: Các lỗi giao thức của Spring — sai URL, sai method, body không đọc được. Chúng đã mang sẵn status
     *     đúng, nhánh này chỉ dịch sang mã của mình. Thiếu nó thì catch-all báo lỗi gõ nhầm thành lỗi hệ thống.
     */
    @ExceptionHandler({
            NoResourceFoundException.class,
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ErrorResponse> handleProtocolFailure(Exception ex) {
        log.debug("Protocol failure: {}", ex.getMessage());

        return switch (ex) {
            case HttpRequestMethodNotSupportedException e -> respond(
                    ErrorCode.METHOD_NOT_ALLOWED,
                    "%s is not supported here".formatted(e.getMethod()),
                    null);
            case HttpMediaTypeNotSupportedException e -> respond(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Content type is not supported",
                    null);
            default -> respond(ErrorCode.NOT_FOUND, "No endpoint for this request", null);
        };
    }

    /**
     * EN: Anything unplanned. Trace goes to the log; the caller learns nothing — messages can name tables and paths.
     * VI: Lỗi ngoài dự tính. Trace vào log; client không biết gì thêm — message có thể lộ tên bảng, đường dẫn.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(ErrorCode.INTERNAL_ERROR, "Something went wrong", null);
    }

    private ResponseEntity<ErrorResponse> respond(
            ErrorCode code, String message, Map<String, String> details) {
        return ResponseEntity
                .status(code.status())
                .body(ErrorResponse.of(code, message, details));
    }
}
