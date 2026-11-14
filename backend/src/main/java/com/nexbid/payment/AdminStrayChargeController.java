package com.nexbid.payment;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * EN: VNPay charges that no payment could use, for an admin to transfer back. Under /api/admin/**, so ADMIN only.
 * VI: Các khoản VNPay đã thu mà không khoản thanh toán nào dùng được, để admin chuyển trả lại. Nằm dưới /api/admin/**,
 *     nên chỉ ADMIN.
 */
@RestController
@RequestMapping("/api/admin/stray-charges")
public class AdminStrayChargeController {

    private final StrayCharges strays;

    public AdminStrayChargeController(StrayCharges strays) {
        this.strays = strays;
    }

    public record StrayRefundRequest(@NotBlank @Size(max = 80) String reference) {
    }

    @GetMapping
    public ApiResponse<List<StrayCharges.StrayCharge>> list() {
        return ApiResponse.of(strays.list());
    }

    @PostMapping("/{id}/refund")
    public ApiResponse<Void> refund(@AuthenticationPrincipal CurrentUser admin, @PathVariable UUID id,
            @Valid @RequestBody StrayRefundRequest request) {
        strays.refund(admin.id(), id, request.reference());
        return ApiResponse.of(null, "Charge refunded");
    }
}
