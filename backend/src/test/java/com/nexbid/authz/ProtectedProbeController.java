package com.nexbid.authz;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;

/**
 * EN: Test-only endpoints behind each rule. Real ones arrive with functions 09 and 14 and inherit the same guard.
 * VI: Endpoint chỉ dùng cho test, đặt sau từng luật. Endpoint thật tới ở chức năng 09 và 14, tự hưởng cùng lớp bảo vệ.
 */
@RestController
class ProtectedProbeController {

    @GetMapping("/api/admin/probe")
    ApiResponse<String> adminArea() {
        return ApiResponse.of("admin area");
    }

    @GetMapping("/api/seller/probe")
    ApiResponse<String> sellerArea() {
        return ApiResponse.of("seller area");
    }

    @GetMapping("/api/probe/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<String> methodGuarded() {
        return ApiResponse.of("method guarded");
    }
}
