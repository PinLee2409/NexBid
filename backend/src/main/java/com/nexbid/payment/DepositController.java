package com.nexbid.payment;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * EN: Deposits: what a lot asks, paying through VNPay, the bidder's own list, and the admin's queue of returns.
 * VI: Tiền cọc: lô yêu cầu gì, đóng qua VNPay, danh sách của người trả giá, và hàng chờ trả lại của admin.
 */
@RestController
public class DepositController {

    private final Deposits deposits;
    private final VnpayService vnpay;

    DepositController(Deposits deposits, VnpayService vnpay) {
        this.deposits = deposits;
        this.vnpay = vnpay;
    }

    /** EN: Public; {@code held} is filled in when signed in. / VI: Công khai; {@code held} có giá trị khi đã đăng nhập. */
    @GetMapping("/api/auctions/{id}/deposit")
    public ApiResponse<Deposits.LotStatus> status(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        return ApiResponse.of(deposits.statusOn(id, me == null ? null : me.id()));
    }

    public record CheckoutRequest(String locale) {
    }

    @PostMapping("/api/auctions/{id}/deposit/vnpay")
    public ApiResponse<Map<String, String>> checkout(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id,
            @RequestBody(required = false) CheckoutRequest request, HttpServletRequest http) {
        VnpayService.Checkout checkout = deposits.start(me.id(), id, http.getRemoteAddr(),
                request == null ? null : request.locale());
        return ApiResponse.of(Map.of("paymentUrl", checkout.paymentUrl()));
    }

    /** EN: The bidder back from VNPay. / VI: Người trả giá quay về từ VNPay. */
    @GetMapping("/api/payments/deposits/vnpay/return")
    public ApiResponse<Deposits.DepositView> vnpayReturn(@RequestParam Map<String, String> fields) {
        VnpayService.Confirmation result = vnpay.confirm(fields);
        return switch (result.outcome()) {
            case CONFIRMED, ALREADY_CONFIRMED -> ApiResponse.of(deposits.view(result.paymentId()),
                    result.paid() ? "Deposit paid" : "Deposit not paid");
            case NOT_FOUND -> throw new ResourceNotFoundException(ErrorCode.DEPOSIT_NOT_FOUND, "No such deposit");
            case INVALID_AMOUNT, INVALID_SIGNATURE -> throw new BusinessException(
                    ErrorCode.PAYMENT_GATEWAY_INVALID, "The reply from VNPay could not be verified");
        };
    }

    @GetMapping("/api/users/me/deposits")
    public ApiResponse<List<Deposits.DepositView>> mine(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(deposits.mine(me.id()));
    }

    @GetMapping("/api/admin/deposits")
    public ApiResponse<List<Deposits.DueView>> due() {
        return ApiResponse.of(deposits.due());
    }

    public record RefundRequest(@NotBlank @Size(max = 80) String reference) {
    }

    @PostMapping("/api/admin/deposits/{id}/refund")
    public ApiResponse<Deposits.DepositView> refund(@AuthenticationPrincipal CurrentUser admin, @PathVariable UUID id,
            @Valid @RequestBody RefundRequest request) {
        return ApiResponse.of(deposits.refunded(admin.id(), id, request.reference()), "Deposit returned");
    }
}
