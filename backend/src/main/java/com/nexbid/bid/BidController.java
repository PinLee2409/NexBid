package com.nexbid.bid;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.auction.PageView;
import com.nexbid.bid.dto.BidHistoryQuery;
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

    /**
     * EN: Bid history (guide §22). Open to anyone, but a token still counts — it is what lets the page
     *     mark which lines are the reader's own.
     * VI: Lịch sử trả giá (guide §22). Ai cũng xem được, nhưng có token vẫn khác — đó là thứ giúp trang
     *     đánh dấu dòng nào là của chính người đọc.
     */
    @GetMapping
    public ApiResponse<PageView<BidView>> history(
            @AuthenticationPrincipal CurrentUser viewer,
            @PathVariable UUID auctionId,
            BidHistoryQuery query) {

        return ApiResponse.of(
                bids.historyOf(auctionId, viewer == null ? null : viewer.id(), query));
    }
}
