package com.nexbid.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * EN: Shipping vouchers, for admins (the /api/admin/** rule already requires the ADMIN role).
 * VI: Voucher phí ship, cho admin (quy tắc /api/admin/** đã bắt buộc vai trò ADMIN).
 */
@RestController
@RequestMapping("/api/admin/promo-codes")
public class AdminPromoController {

    private final PromoCodes promos;

    public AdminPromoController(PromoCodes promos) {
        this.promos = promos;
    }

    public record CreatePromoRequest(
            @NotBlank @Size(max = 32) String code,
            @NotNull @DecimalMin(value = "1000") BigDecimal amount,
            @NotNull @Min(1) @Max(100000) Integer maxUses,
            Instant expiresAt,
            @Size(max = 200) String note) {
    }

    @GetMapping
    public ApiResponse<List<PromoCodes.Promo>> list() {
        return ApiResponse.of(promos.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PromoCodes.Promo> create(@AuthenticationPrincipal CurrentUser me,
            @Valid @RequestBody CreatePromoRequest request) {
        return ApiResponse.of(promos.create(me.id(), request.code(), request.amount(), request.maxUses(),
                request.expiresAt(), request.note(), Instant.now()), "Voucher created");
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    @PostMapping("/{code}/active")
    public ApiResponse<Map<String, Boolean>> setActive(@PathVariable String code,
            @Valid @RequestBody ActiveRequest request) {
        promos.setActive(code, request.active());
        return ApiResponse.of(Map.of("active", request.active()));
    }
}
