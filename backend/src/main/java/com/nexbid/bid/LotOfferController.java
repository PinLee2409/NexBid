package com.nexbid.bid;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * EN: Offers, from the buyer's side (on a lot and in their account) and the seller's (under /api/seller/**).
 * VI: Đề nghị, từ phía người mua (trên lô và trong tài khoản) và phía người bán (dưới /api/seller/**).
 */
@RestController
public class LotOfferController {

    private final LotOffers offers;

    LotOfferController(LotOffers offers) {
        this.offers = offers;
    }

    public record OfferRequest(
            @NotNull @DecimalMin(value = "0", inclusive = false, message = "An offer must be above zero") BigDecimal amount,
            @Size(max = 300, message = "A message is at most 300 characters") String message) {
    }

    public record CounterRequest(
            @NotNull @DecimalMin(value = "0", inclusive = false, message = "A counter must be above zero") BigDecimal amount) {
    }

    @PostMapping("/api/auctions/{auctionId}/offers")
    public ApiResponse<LotOffers.OfferView> make(@AuthenticationPrincipal CurrentUser buyer,
            @PathVariable UUID auctionId, @Valid @RequestBody OfferRequest request) {
        return ApiResponse.of(offers.make(buyer.id(), auctionId, request.amount(), request.message()), "Offer sent");
    }

    @GetMapping("/api/auctions/{auctionId}/offers/mine")
    public ApiResponse<List<LotOffers.OfferView>> mineOnLot(@AuthenticationPrincipal CurrentUser buyer,
            @PathVariable UUID auctionId) {
        return ApiResponse.of(offers.forBuyerOnLot(buyer.id(), auctionId));
    }

    @GetMapping("/api/users/me/lot-offers")
    public ApiResponse<List<LotOffers.OfferView>> mine(@AuthenticationPrincipal CurrentUser buyer) {
        return ApiResponse.of(offers.forBuyer(buyer.id()));
    }

    @PostMapping("/api/users/me/lot-offers/{id}/accept-counter")
    public ApiResponse<LotOffers.OfferView> acceptCounter(@AuthenticationPrincipal CurrentUser buyer,
            @PathVariable UUID id) {
        return ApiResponse.of(offers.acceptCounter(buyer.id(), id), "Bought");
    }

    @PostMapping("/api/users/me/lot-offers/{id}/decline-counter")
    public ApiResponse<LotOffers.OfferView> declineCounter(@AuthenticationPrincipal CurrentUser buyer,
            @PathVariable UUID id) {
        return ApiResponse.of(offers.declineCounter(buyer.id(), id), "Counter declined");
    }

    @PostMapping("/api/users/me/lot-offers/{id}/withdraw")
    public ApiResponse<LotOffers.OfferView> withdraw(@AuthenticationPrincipal CurrentUser buyer,
            @PathVariable UUID id) {
        return ApiResponse.of(offers.withdraw(buyer.id(), id), "Offer withdrawn");
    }

    @GetMapping("/api/seller/lot-offers")
    public ApiResponse<List<LotOffers.OfferView>> forSeller(@AuthenticationPrincipal CurrentUser seller) {
        return ApiResponse.of(offers.forSeller(seller.id()));
    }

    @PostMapping("/api/seller/lot-offers/{id}/accept")
    public ApiResponse<LotOffers.OfferView> accept(@AuthenticationPrincipal CurrentUser seller, @PathVariable UUID id) {
        return ApiResponse.of(offers.accept(seller.id(), id), "Offer accepted");
    }

    @PostMapping("/api/seller/lot-offers/{id}/decline")
    public ApiResponse<LotOffers.OfferView> decline(@AuthenticationPrincipal CurrentUser seller, @PathVariable UUID id) {
        return ApiResponse.of(offers.decline(seller.id(), id), "Offer declined");
    }

    @PostMapping("/api/seller/lot-offers/{id}/counter")
    public ApiResponse<LotOffers.OfferView> counter(@AuthenticationPrincipal CurrentUser seller, @PathVariable UUID id,
            @Valid @RequestBody CounterRequest request) {
        return ApiResponse.of(offers.counter(seller.id(), id, request.amount()), "Counter sent");
    }
}
