package com.nexbid.order;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

/**
 * EN: The buyer's orders (guide §33). The buyer always comes from the token.
 * VI: Đơn hàng của người mua (guide §33). Người mua luôn lấy từ token.
 */
@RestController
@RequestMapping("/api/users/me/orders")
public class OrderController {

    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    public ApiResponse<List<OrderView>> mine(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(orders.listFor(me.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderView> one(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        return ApiResponse.of(orders.get(me.id(), id));
    }
}
