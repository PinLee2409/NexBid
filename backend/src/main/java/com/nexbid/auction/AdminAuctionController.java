package com.nexbid.auction;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.nexbid.auction.dto.RejectAuctionRequest;
import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;

/**
 * EN: The review queue (guide §16). ADMIN comes from the /api/admin/** pattern set at function 06.
 * VI: Hàng chờ duyệt (guide §16). Quyền ADMIN đến từ mẫu /api/admin/** đặt ở chức năng 06.
 */
@RestController
@RequestMapping("/api/admin/auctions")
public class AdminAuctionController {

    private final AuctionService auctions;

    public AdminAuctionController(AuctionService auctions) {
        this.auctions = auctions;
    }

    @GetMapping("/pending")
    public ApiResponse<List<AuctionView>> pending() {
        return ApiResponse.of(auctions.listPendingApproval());
    }

    /**
     * EN: The terms, the item, its photos and the seller — everything a decision needs, in one response.
     * VI: Điều khoản, món hàng, ảnh và người bán — mọi thứ cần để quyết định, trong một response.
     */
    @GetMapping("/{id}")
    public ApiResponse<AuctionDetailView> one(@PathVariable UUID id) {
        return ApiResponse.of(auctions.getForReview(id));
    }

    /**
     * EN: Lets the lot through. From here it is public, and the product is locked to it.
     * VI: Cho lô đi tiếp. Từ đây nó công khai, và sản phẩm bị khoá vào phiên này.
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<AuctionView> approve(@AuthenticationPrincipal CurrentUser admin, @PathVariable UUID id) {
        return ApiResponse.of(auctions.approve(id, admin.id()), "Auction approved");
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<AuctionView> reject(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable UUID id, @Valid @RequestBody RejectAuctionRequest request) {

        return ApiResponse.of(auctions.reject(id, admin.id(), request.reason()), "Auction rejected");
    }
}
