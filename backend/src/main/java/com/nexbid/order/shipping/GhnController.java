package com.nexbid.order.shipping;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nexbid.common.response.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * EN: GHN for the site: whether it is connected, address lists and fees (signed in), and GHN's own webhook (public,
 *     checked by the secret in its URL; an unknown secret answers 404, as if there were no such endpoint).
 * VI: GHN cho trang web: đã kết nối chưa, danh sách địa chỉ và phí ship (cần đăng nhập), và webhook của chính GHN (công
 *     khai, kiểm bằng secret trong URL; secret sai trả 404, như thể không có endpoint này).
 */
@RestController
public class GhnController {

    private final GhnShipping ghn;

    public GhnController(GhnShipping ghn) {
        this.ghn = ghn;
    }

    public record FeeRequest(@Min(1) int fromDistrictId, @Min(1) int toDistrictId, @NotBlank String toWardCode,
            @Min(1) @Max(30000) int weightGrams, @Min(1) @Max(150) int lengthCm, @Min(1) @Max(150) int widthCm,
            @Min(1) @Max(150) int heightCm, @PositiveOrZero long insuranceValue) {
    }

    /** EN: What GHN posts; it names fields in PascalCase. / VI: Những gì GHN gửi; GHN đặt tên trường kiểu PascalCase. */
    public record GhnUpdate(@JsonProperty("OrderCode") String orderCode, @JsonProperty("Status") String status) {
    }

    @GetMapping("/api/shipping/status")
    public ApiResponse<Map<String, Boolean>> status() {
        return ApiResponse.of(Map.of("ghn", ghn.enabled()));
    }

    @GetMapping("/api/shipping/ghn/provinces")
    public ApiResponse<List<GhnClient.Area>> provinces() {
        return ApiResponse.of(ghn.provinces());
    }

    @GetMapping("/api/shipping/ghn/districts")
    public ApiResponse<List<GhnClient.Area>> districts(@RequestParam int provinceId) {
        return ApiResponse.of(ghn.districts(provinceId));
    }

    @GetMapping("/api/shipping/ghn/wards")
    public ApiResponse<List<GhnClient.Area>> wards(@RequestParam int districtId) {
        return ApiResponse.of(ghn.wards(districtId));
    }

    @PostMapping("/api/shipping/ghn/fee")
    public ApiResponse<Map<String, BigDecimal>> fee(@Valid @RequestBody FeeRequest request) {
        return ApiResponse.of(Map.of("fee", ghn.fee(new GhnClient.FeeQuery(request.fromDistrictId(),
                request.toDistrictId(), request.toWardCode(), request.weightGrams(), request.lengthCm(),
                request.widthCm(), request.heightCm(), request.insuranceValue()))));
    }

    @PostMapping("/api/shipping/ghn/webhook")
    public ResponseEntity<Map<String, Boolean>> webhook(@RequestParam(required = false) String secret,
            @RequestBody GhnUpdate update) {
        if (!ghn.acceptsWebhook(secret)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(Map.of("recorded", ghn.record(update.orderCode(), update.status(), Instant.now())));
    }
}
