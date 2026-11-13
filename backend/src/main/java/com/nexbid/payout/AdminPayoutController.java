package com.nexbid.payout;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;
import com.nexbid.payout.dto.PayoutRequest;

import jakarta.validation.Valid;

/**
 * EN: Sellers waiting for their money, and recording the transfers. ADMIN comes from the /api/admin/** pattern.
 * VI: Người bán đang chờ tiền, và ghi nhận các lần chuyển khoản. Quyền ADMIN đến từ mẫu /api/admin/**.
 */
@RestController
@RequestMapping("/api/admin/payouts")
public class AdminPayoutController {

    private final PayoutService payouts;

    public AdminPayoutController(PayoutService payouts) {
        this.payouts = payouts;
    }

    @GetMapping("/due")
    public ApiResponse<List<PayoutService.PayoutDue>> due() {
        return ApiResponse.of(payouts.due());
    }

    @GetMapping
    public ApiResponse<List<PayoutService.Payout>> recent() {
        return ApiResponse.of(payouts.recentPayouts());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PayoutService.Payout> payOut(
            @AuthenticationPrincipal CurrentUser admin, @Valid @RequestBody PayoutRequest request) {
        return ApiResponse.of(payouts.payOut(admin.id(), request.sellerId(), request.reference()), "Payout recorded");
    }
}
