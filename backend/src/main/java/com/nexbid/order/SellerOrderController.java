package com.nexbid.order;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

/**
 * EN: The seller's side of their sales (spec §5.3). SELLER comes from the /api/seller/** pattern.
 * VI: Phía người bán của các giao dịch (spec §5.3). Quyền SELLER đến từ mẫu /api/seller/**.
 */
@RestController
@RequestMapping("/api/seller/orders")
public class SellerOrderController {

    private final OrderService orders;

    public SellerOrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    public ApiResponse<List<OrderView>> sold(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(orders.listForSeller(me.id()));
    }

    @PostMapping("/{id}/ship")
    public ApiResponse<OrderView> ship(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        return ApiResponse.of(orders.ship(me.id(), id), "Order marked as shipped");
    }
}
