package com.nexbid.payout;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

/**
 * EN: A seller's earnings. SELLER comes from the /api/seller/** pattern. The bank account is everyone's, at
 *     /api/users/me/bank-account.
 * VI: Thu nhập của người bán. Quyền SELLER đến từ mẫu /api/seller/**. Tài khoản ngân hàng là của mọi người dùng, ở
 *     /api/users/me/bank-account.
 */
@RestController
@RequestMapping("/api/seller")
public class SellerPayoutController {

    private final PayoutService payouts;

    public SellerPayoutController(PayoutService payouts) {
        this.payouts = payouts;
    }

    @GetMapping("/earnings")
    public ApiResponse<PayoutService.SellerEarnings> earnings(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(payouts.earningsOf(me.id()));
    }
}
