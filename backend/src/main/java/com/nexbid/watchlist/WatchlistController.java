package com.nexbid.watchlist;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

/**
 * EN: Watch buttons and the watchlist page (guide §28). Who is watching always comes from the token.
 * VI: Nút theo dõi và trang danh sách theo dõi (guide §28). Ai đang theo dõi luôn lấy từ token.
 */
@RestController
public class WatchlistController {

    private final WatchlistService watchlist;

    public WatchlistController(WatchlistService watchlist) {
        this.watchlist = watchlist;
    }

    @PostMapping("/api/auctions/{auctionId}/watch")
    public ApiResponse<WatchState> watch(
            @AuthenticationPrincipal CurrentUser me, @PathVariable UUID auctionId) {

        return ApiResponse.of(watchlist.watch(me.id(), auctionId), "Watching");
    }

    @DeleteMapping("/api/auctions/{auctionId}/watch")
    public ApiResponse<WatchState> unwatch(
            @AuthenticationPrincipal CurrentUser me, @PathVariable UUID auctionId) {

        return ApiResponse.of(watchlist.unwatch(me.id(), auctionId), "No longer watching");
    }

    @GetMapping("/api/users/me/watchlist")
    public ApiResponse<List<AuctionSummaryView>> mine(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(watchlist.watchlistOf(me.id()));
    }
}
