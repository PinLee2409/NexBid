package com.nexbid.payment;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;
import com.nexbid.payment.dto.PayRequest;

import jakarta.validation.Valid;

/**
 * EN: Payment endpoints (guide §32). The owner always comes from the token.
 * VI: Các endpoint thanh toán (guide §32). Chủ nhân luôn lấy từ token.
 */
@RestController
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
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

    @PostMapping("/api/payments/{id}/pay")
    public ApiResponse<PaymentView> pay(
            @AuthenticationPrincipal CurrentUser me,
            @PathVariable UUID id,
            @Valid @RequestBody PayRequest request) {

        PaymentView result = payments.pay(me.id(), id, request.outcome());
        return ApiResponse.of(result, request.outcome() == PaymentOutcome.SUCCESS ? "Payment successful" : "Payment failed");
    }
}
