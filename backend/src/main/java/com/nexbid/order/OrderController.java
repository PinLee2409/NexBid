package com.nexbid.order;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;
import com.nexbid.order.dto.ReviewRequest;

import jakarta.validation.Valid;

/**
 * EN: The buyer's orders (guide §33). The buyer always comes from the token.
 * VI: Đơn hàng của người mua (guide §33). Người mua luôn lấy từ token.
 */
@RestController
@RequestMapping("/api/users/me/orders")
public class OrderController {

    private final OrderService orders;
    private final ReviewService reviews;

    public OrderController(OrderService orders, ReviewService reviews) {
        this.orders = orders;
        this.reviews = reviews;
    }

    @GetMapping
    public ApiResponse<List<OrderView>> mine(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(orders.listFor(me.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderView> one(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        return ApiResponse.of(orders.get(me.id(), id));
    }

    /** EN: The buyer confirms the goods arrived. / VI: Người mua xác nhận đã nhận hàng. */
    @PostMapping("/{id}/received")
    public ApiResponse<OrderView> received(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        return ApiResponse.of(orders.confirmReceipt(me.id(), id), "Order received");
    }

    /** EN: Once the goods arrived, the buyer rates the seller, once. / VI: Khi hàng đã tới, người mua đánh giá người bán, một lần. */
    @PostMapping("/{id}/review")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewView> review(
            @AuthenticationPrincipal CurrentUser me, @PathVariable UUID id, @Valid @RequestBody ReviewRequest request) {
        return ApiResponse.of(reviews.review(me.id(), id, request.rating(), request.comment()), "Review posted");
    }
}
