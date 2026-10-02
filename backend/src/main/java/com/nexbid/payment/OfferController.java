package com.nexbid.payment;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

/**
 * EN: Second-chance offers made to the caller (spec §17). The buyer always comes from the token.
 * VI: Các đề nghị cơ hội thứ hai gửi tới người gọi (spec §17). Người mua luôn lấy từ token.
 */
@RestController
@RequestMapping("/api/users/me/offers")
public class OfferController {

    private final SecondChanceService offers;

    OfferController(SecondChanceService offers) {
        this.offers = offers;
    }

    @GetMapping
    public ApiResponse<List<OfferView>> mine(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(offers.mine(me.id()));
    }

    @PostMapping("/{id}/accept")
    public ApiResponse<OfferView.Details> accept(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        return ApiResponse.of(offers.accept(me.id(), id), "Offer accepted");
    }

    @PostMapping("/{id}/decline")
    public ApiResponse<OfferView.Details> decline(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        return ApiResponse.of(offers.decline(me.id(), id), "Offer declined");
    }
}
