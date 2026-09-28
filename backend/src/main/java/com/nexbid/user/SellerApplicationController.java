package com.nexbid.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;
import com.nexbid.user.dto.SellerApplicationRequest;

import jakarta.validation.Valid;

/**
 * EN: A buyer's own request to become a seller (spec §7.1). No data means they never asked.
 * VI: Yêu cầu trở thành người bán của chính người mua (spec §7.1). Không có data nghĩa là họ chưa từng gửi.
 */
@RestController
@RequestMapping("/api/users/me/seller-application")
public class SellerApplicationController {

    private final SellerApplicationService applications;

    SellerApplicationController(SellerApplicationService applications) {
        this.applications = applications;
    }

    @GetMapping
    public ApiResponse<SellerApplicationView> latest(@AuthenticationPrincipal CurrentUser caller) {
        return ApiResponse.of(applications.latestOf(caller.id()).orElse(null));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SellerApplicationView> apply(
            @AuthenticationPrincipal CurrentUser caller, @Valid @RequestBody SellerApplicationRequest request) {
        return ApiResponse.of(applications.apply(caller.id(), request.note()), "Request sent");
    }
}
