package com.nexbid.order;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.auction.PageView;
import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;
import com.nexbid.order.dto.RefundRequest;
import com.nexbid.user.BankAccount;

import jakarta.validation.Valid;

/**
 * EN: Every order, and the refund. ADMIN comes from the /api/admin/** pattern.
 * VI: Mọi đơn hàng, và việc hoàn tiền. Quyền ADMIN đến từ mẫu /api/admin/**.
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orders;

    public AdminOrderController(OrderService orders) {
        this.orders = orders;
    }

    /** EN: E.g. ?status=PAID&status=PROCESSING; no status means all. / VI: Ví dụ ?status=PAID&status=PROCESSING; không truyền là tất cả. */
    @GetMapping
    public ApiResponse<PageView<OrderView>> byStatus(
            @RequestParam(required = false) List<OrderStatus> status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.of(orders.listForAdmin(status, page, size));
    }

    /** EN: Where to transfer the refund; null when the buyer has not said. / VI: Chuyển tiền hoàn về đâu; null khi người mua chưa cho biết. */
    @GetMapping("/{id}/refund-account")
    public ApiResponse<BankAccount> refundAccount(@PathVariable UUID id) {
        return ApiResponse.of(orders.refundAccountOf(id));
    }

    /** EN: Records a transfer already made to the buyer. / VI: Ghi nhận lần chuyển khoản đã trả lại người mua. */
    @PostMapping("/{id}/refund")
    public ApiResponse<OrderView> refund(@AuthenticationPrincipal CurrentUser admin, @PathVariable UUID id,
            @Valid @RequestBody RefundRequest request) {
        return ApiResponse.of(orders.refund(admin.id(), id, request.reference()), "Order refunded");
    }
}
