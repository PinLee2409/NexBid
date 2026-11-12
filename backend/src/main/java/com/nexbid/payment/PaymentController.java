package com.nexbid.payment;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

/**
 * EN: Payment endpoints (guide §32). The owner always comes from the token; VNPay's two callbacks are public and
 *     trusted only through their signature.
 * VI: Các endpoint thanh toán (guide §32). Chủ nhân luôn lấy từ token; hai lời gọi lại của VNPay là công khai và chỉ
 *     được tin qua chữ ký của chúng.
 */
@RestController
public class PaymentController {

    private final PaymentService payments;
    private final VnpayService vnpay;

    public PaymentController(PaymentService payments, VnpayService vnpay) {
        this.payments = payments;
        this.vnpay = vnpay;
    }

    /**
     * EN: Not in the spec's API list, but its /payments page (spec §36) needs it — and without it a winner
     *     has no way to learn their payment id.
     * VI: Không có trong danh sách API của spec, nhưng trang /payments của spec (§36) cần nó — thiếu nó thì
     *     người thắng không có cách nào biết id khoản thanh toán của mình.
     */
    @GetMapping("/api/users/me/payments")
    public ApiResponse<List<PaymentView>> mine(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(payments.listFor(me.id()));
    }

    @GetMapping("/api/payments/{id}")
    public ApiResponse<PaymentView> one(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        return ApiResponse.of(payments.get(me.id(), id));
    }

    /** EN: The page language, so VNPay's page matches it. / VI: Ngôn ngữ của trang, để trang VNPay khớp theo. */
    public record CheckoutRequest(String locale) {
    }

    /**
     * EN: Starts paying through VNPay: the client sends the buyer to {@code paymentUrl}.
     * VI: Bắt đầu thanh toán qua VNPay: client đưa người mua tới {@code paymentUrl}.
     */
    @PostMapping("/api/payments/{id}/vnpay")
    public ApiResponse<Map<String, String>> checkout(
            @AuthenticationPrincipal CurrentUser me,
            @PathVariable UUID id,
            @RequestBody(required = false) CheckoutRequest request,
            HttpServletRequest http) {

        VnpayService.Checkout checkout = vnpay.startCheckout(
                me.id(), id, http.getRemoteAddr(), request == null ? null : request.locale());
        return ApiResponse.of(Map.of("paymentUrl", checkout.paymentUrl()));
    }

    /**
     * EN: VNPay's server-to-server report (IPN). The answer is VNPay's own format, not the API envelope; VNPay
     *     retries until it reads RspCode 00 or 02.
     * VI: Lời báo server tới server của VNPay (IPN). Câu trả lời theo định dạng của VNPay, không phải khung API; VNPay
     *     gửi lại cho tới khi đọc được RspCode 00 hoặc 02.
     */
    @GetMapping("/api/payments/vnpay/ipn")
    public Map<String, String> ipn(@RequestParam Map<String, String> fields) {
        try {
            VnpayService.Confirmation result = vnpay.confirm(fields);
            return switch (result.outcome()) {
                case CONFIRMED -> reply("00", "Confirm Success");
                case ALREADY_CONFIRMED -> reply("02", "Order already confirmed");
                case NOT_FOUND -> reply("01", "Order not found");
                case INVALID_AMOUNT -> reply("04", "Invalid amount");
                case INVALID_SIGNATURE -> reply("97", "Invalid signature");
            };
        } catch (RuntimeException ex) {
            // EN: Rolled back; VNPay will try again. / VI: Đã rollback; VNPay sẽ thử lại.
            return reply("99", "Unknown error");
        }
    }

    /**
     * EN: The buyer's browser coming back from VNPay. It confirms too — on a machine VNPay's IPN cannot reach, this is
     *     the only report — and that is safe because the signature is checked and confirming is idempotent.
     * VI: Trình duyệt người mua quay về từ VNPay. Nó cũng xác nhận — trên máy mà IPN của VNPay không với tới, đây là lời
     *     báo duy nhất — và điều đó an toàn vì chữ ký được kiểm và việc xác nhận là idempotent.
     */
    @GetMapping("/api/payments/vnpay/return")
    public ApiResponse<PaymentView> vnpayReturn(@RequestParam Map<String, String> fields) {
        VnpayService.Confirmation result = vnpay.confirm(fields);
        return switch (result.outcome()) {
            case CONFIRMED, ALREADY_CONFIRMED -> ApiResponse.of(payments.viewOf(result.paymentId()),
                    result.paid() ? "Payment successful" : "Payment failed");
            case NOT_FOUND -> throw new ResourceNotFoundException(ErrorCode.PAYMENT_NOT_FOUND, "No such VNPay attempt");
            case INVALID_AMOUNT, INVALID_SIGNATURE -> throw new BusinessException(
                    ErrorCode.PAYMENT_GATEWAY_INVALID, "The reply from VNPay could not be verified");
        };
    }

    private static Map<String, String> reply(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }
}
