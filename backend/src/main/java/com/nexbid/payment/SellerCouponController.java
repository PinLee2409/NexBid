package com.nexbid.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
import jakarta.validation.constraints.NotNull;

/**
 * EN: A seller's own discount codes. Under /api/seller/**, so SELLER only.
 * VI: Mã giảm giá riêng của người bán. Nằm dưới /api/seller/**, nên chỉ người bán.
 */
@RestController
@RequestMapping("/api/seller/coupons")
public class SellerCouponController {

    private final SellerCoupons coupons;

    SellerCouponController(SellerCoupons coupons) {
        this.coupons = coupons;
    }

    public record CouponRequest(@NotBlank String code, Integer percentOff, BigDecimal amountOff, BigDecimal minPrice,
            Boolean followersOnly, @NotNull Integer maxUses, Instant expiresAt) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    @GetMapping
    public ApiResponse<List<SellerCoupons.Coupon>> mine(@AuthenticationPrincipal CurrentUser seller) {
        return ApiResponse.of(coupons.mine(seller.id()));
    }

    @PostMapping
    public ApiResponse<SellerCoupons.Coupon> create(@AuthenticationPrincipal CurrentUser seller,
            @Valid @RequestBody CouponRequest request) {
        return ApiResponse.of(coupons.create(seller.id(), request.code(), request.percentOff(), request.amountOff(),
                request.minPrice(), Boolean.TRUE.equals(request.followersOnly()), request.maxUses(),
                request.expiresAt(), Instant.now()), "Code created");
    }

    @PostMapping("/{code}/active")
    public ApiResponse<SellerCoupons.Coupon> setActive(@AuthenticationPrincipal CurrentUser seller,
            @PathVariable String code, @Valid @RequestBody ActiveRequest request) {
        return ApiResponse.of(coupons.setActive(seller.id(), code, request.active()));
    }
}
