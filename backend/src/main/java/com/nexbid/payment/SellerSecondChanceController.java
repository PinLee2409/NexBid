package com.nexbid.payment;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

/**
 * EN: The seller's side of second-chance offers (spec §17): what can be offered, and offering it.
 * VI: Phía người bán của đề nghị cơ hội thứ hai (spec §17): lô nào đề nghị được, và gửi đề nghị.
 */
@RestController
public class SellerSecondChanceController {

    private final SecondChanceService offers;

    SellerSecondChanceController(SecondChanceService offers) {
        this.offers = offers;
    }

    @GetMapping("/api/seller/second-chances")
    public ApiResponse<List<SecondChanceView>> list(@AuthenticationPrincipal CurrentUser seller) {
        return ApiResponse.of(offers.forSeller(seller.id()));
    }

    @PostMapping("/api/seller/auctions/{auctionId}/second-chance")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OfferView.Details> offer(@AuthenticationPrincipal CurrentUser seller, @PathVariable UUID auctionId) {
        return ApiResponse.of(offers.offer(seller.id(), auctionId), "Offer sent");
    }
}
