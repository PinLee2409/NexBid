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
    private final SellerStatements statements;

    public SellerPayoutController(PayoutService payouts, SellerStatements statements) {
        this.payouts = payouts;
        this.statements = statements;
    }

    /** EN: One month's statement, e.g. ?month=2026-11. / VI: Bảng kê của một tháng, ví dụ ?month=2026-11. */
    @GetMapping("/statements")
    public ApiResponse<SellerStatements.Statement> statement(@AuthenticationPrincipal CurrentUser me,
            @org.springframework.web.bind.annotation.RequestParam java.time.YearMonth month) {
        return ApiResponse.of(statements.of(me.id(), month));
    }

    @GetMapping("/statements.csv")
    public org.springframework.http.ResponseEntity<byte[]> statementCsv(@AuthenticationPrincipal CurrentUser me,
            @org.springframework.web.bind.annotation.RequestParam java.time.YearMonth month) {
        return com.nexbid.common.CsvExport.download("nexbid-statement-" + month + ".csv",
                SellerStatements.rows(statements.of(me.id(), month)));
    }

    @GetMapping("/earnings")
    public ApiResponse<PayoutService.SellerEarnings> earnings(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(payouts.earningsOf(me.id()));
    }
}
