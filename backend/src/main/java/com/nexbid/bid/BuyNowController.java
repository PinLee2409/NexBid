package com.nexbid.bid;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

/**
 * EN: Buy-now: ends a lot at its fixed price, until the first bid. Signed in only, like placing a bid.
 * VI: Mua ngay: kết thúc lô ở giá cố định, cho tới lượt trả giá đầu tiên. Phải đăng nhập, như khi trả giá.
 */
@RestController
public class BuyNowController {

    private final BidService bids;

    BuyNowController(BidService bids) {
        this.bids = bids;
    }

    @PostMapping("/api/auctions/{auctionId}/buy-now")
    public ApiResponse<PlacedBidView> buyNow(@AuthenticationPrincipal CurrentUser buyer, @PathVariable UUID auctionId) {
        return ApiResponse.of(bids.buyNow(auctionId, buyer.id()), "Bought");
    }
}
