package com.nexbid.bid;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.bid.dto.PlaceBidRequest;
import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;

/**
 * EN: Placing a bid (guide §20). Reading a lot is public, but offering money is not — this path is the one
 *     exception carved out of the public /api/auctions/** rule.
 * VI: Đặt giá (guide §20). Xem một lô là công khai, còn bỏ tiền ra thì không — đường dẫn này là ngoại lệ
 *     duy nhất khoét ra khỏi luật công khai /api/auctions/**.
 */
@RestController
@RequestMapping("/api/auctions/{auctionId}/bids")
public class BidController {

    private final BidService bids;

    public BidController(BidService bids) {
        this.bids = bids;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PlacedBidView> place(
            @AuthenticationPrincipal CurrentUser bidder,
            @PathVariable UUID auctionId,
            @Valid @RequestBody PlaceBidRequest request) {

        return ApiResponse.of(bids.place(auctionId, bidder.id(), request.amount()), "Bid placed");
    }
}
