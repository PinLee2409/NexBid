package com.nexbid.bid;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

/** EN: `GET /api/users/me/bids` (spec §27). / VI: `GET /api/users/me/bids` (spec §27). */
@RestController
public class MyBidsController {

    private final BidService bids;

    MyBidsController(BidService bids) {
        this.bids = bids;
    }

    @GetMapping("/api/users/me/bids")
    public ApiResponse<List<MyBidView>> mine(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(bids.lotsBidOnBy(me.id()));
    }
}
