package com.nexbid.common.exception;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Positive;

/**
 * EN: Test-only endpoints, one per way of failing. In src/test, so it can never be deployed.
 * VI: Endpoint chỉ dùng cho test, mỗi kiểu lỗi một cái. Nằm trong src/test nên không bao giờ lên production.
 */
@RestController
@RequestMapping("/__test")
class ProbeController {

    record Payload(@Email String email, @Positive Integer amount) {
    }

    @GetMapping("/ok")
    ApiResponse<Map<String, Integer>> ok() {
        return ApiResponse.of(Map.of("lot", 7), "Fetched");
    }

    @GetMapping("/business")
    ApiResponse<Void> business() {
        throw new BusinessException(ErrorCode.BID_TOO_LOW, "Bid must be at least 18500000");
    }

    @GetMapping("/missing")
    ApiResponse<Void> missing() {
        throw new ResourceNotFoundException(ErrorCode.AUCTION_NOT_FOUND, "No such auction");
    }

    @PostMapping("/validate")
    ApiResponse<Void> validate(@Valid @RequestBody Payload payload) {
        return ApiResponse.ok("Accepted");
    }

    @GetMapping("/boom")
    ApiResponse<Void> boom() {
        throw new IllegalStateException("jdbc connection to nexbid_secret failed");
    }
}
