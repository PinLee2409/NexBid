package com.nexbid.user;

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
import com.nexbid.user.dto.RejectSellerApplicationRequest;

import jakarta.validation.Valid;

/** EN: The queue of requests to become a seller. / VI: Hàng chờ các yêu cầu trở thành người bán. */
@RestController
@RequestMapping("/api/admin/seller-applications")
public class AdminSellerApplicationController {

    private final SellerApplicationService applications;

    AdminSellerApplicationController(SellerApplicationService applications) {
        this.applications = applications;
    }

    /** EN: Every request when `status` is left out. / VI: Mọi yêu cầu nếu bỏ trống `status`. */
    @GetMapping
    public ApiResponse<SellerApplicationPage> list(
            @RequestParam(required = false) SellerApplicationStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.of(applications.list(status, page, size));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<SellerApplicationView> approve(@AuthenticationPrincipal CurrentUser admin, @PathVariable UUID id) {
        return ApiResponse.of(applications.approve(id, admin.id()), "Seller approved");
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<SellerApplicationView> reject(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable UUID id,
            @Valid @RequestBody RejectSellerApplicationRequest request) {
        return ApiResponse.of(applications.reject(id, admin.id(), request.reason()), "Request rejected");
    }
}
