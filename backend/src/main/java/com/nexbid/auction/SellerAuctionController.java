package com.nexbid.auction;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.auction.dto.CreateAuctionRequest;
import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;

/**
 * EN: A seller's own auctions (guide §14). SELLER comes from the /api/seller/** pattern set at function 06.
 * VI: Phiên đấu giá của chính người bán (guide §14). Quyền SELLER đến từ mẫu /api/seller/** đặt ở chức năng 06.
 */
@RestController
@RequestMapping("/api/seller/auctions")
public class SellerAuctionController {

    private final AuctionService auctions;

    public SellerAuctionController(AuctionService auctions) {
        this.auctions = auctions;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuctionView> create(
            @AuthenticationPrincipal CurrentUser seller,
            @Valid @RequestBody CreateAuctionRequest request) {

        return ApiResponse.of(auctions.create(seller.id(), request), "Auction created as a draft");
    }

    @GetMapping
    public ApiResponse<List<AuctionView>> mine(@AuthenticationPrincipal CurrentUser seller) {
        return ApiResponse.of(auctions.listOwnedBy(seller.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<AuctionView> one(
            @AuthenticationPrincipal CurrentUser seller, @PathVariable UUID id) {

        return ApiResponse.of(auctions.getOwned(seller.id(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<AuctionView> update(
            @AuthenticationPrincipal CurrentUser seller,
            @PathVariable UUID id,
            @Valid @RequestBody CreateAuctionRequest request) {

        return ApiResponse.of(auctions.update(seller.id(), id, request), "Auction updated");
    }

    /**
     * EN: Hands the lot to an admin (guide §15). After this the seller waits.
     * VI: Giao lô cho admin (guide §15). Sau bước này người bán chỉ còn việc chờ.
     */
    @PostMapping("/{id}/submit")
    public ApiResponse<AuctionView> submit(
            @AuthenticationPrincipal CurrentUser seller, @PathVariable UUID id) {

        return ApiResponse.of(auctions.submitForApproval(seller.id(), id), "Sent for approval");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancelDraft(
            @AuthenticationPrincipal CurrentUser seller, @PathVariable UUID id) {

        auctions.cancelDraft(seller.id(), id);
        return ApiResponse.ok("Draft auction removed");
    }
}
