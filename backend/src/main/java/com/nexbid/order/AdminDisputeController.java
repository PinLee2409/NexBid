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

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;
import com.nexbid.order.dto.ResolveDisputeRequest;

import jakarta.validation.Valid;

/**
 * EN: Buyers' disputes, and the admin's decision. ADMIN comes from the /api/admin/** pattern.
 * VI: Khiếu nại của người mua, và quyết định của admin. Quyền ADMIN đến từ mẫu /api/admin/**.
 */
@RestController
@RequestMapping("/api/admin/disputes")
public class AdminDisputeController {

    private final OrderService orders;

    public AdminDisputeController(OrderService orders) {
        this.orders = orders;
    }

    /** EN: ?status=OPEN (default), REFUNDED or REJECTED. / VI: ?status=OPEN (mặc định), REFUNDED hoặc REJECTED. */
    @GetMapping
    public ApiResponse<List<OrderService.DisputeEntry>> byStatus(
            @RequestParam(defaultValue = "OPEN") DisputeBook.Status status) {
        return ApiResponse.of(orders.disputes(status.name()));
    }

    @PostMapping("/{id}/resolve")
    public ApiResponse<OrderView> resolve(@AuthenticationPrincipal CurrentUser admin, @PathVariable UUID id,
            @Valid @RequestBody ResolveDisputeRequest request) {
        return ApiResponse.of(orders.resolveDispute(admin.id(), id, request.refund(), request.note(), request.reference()),
                request.refund() ? "Dispute refunded" : "Dispute rejected");
    }
}
