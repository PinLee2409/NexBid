package com.nexbid.auction;

import java.time.Instant;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;

/**
 * EN: Typing help for the search box, and past prices for a lot. Both public, like browsing.
 * VI: Gợi ý khi gõ cho ô tìm kiếm, và giá đã bán cho một lô. Cả hai công khai, như việc duyệt hàng.
 */
@RestController
public class SearchController {

    private final SearchSuggestions suggestions;
    private final AuctionService auctions;

    SearchController(SearchSuggestions suggestions, AuctionService auctions) {
        this.suggestions = suggestions;
        this.auctions = auctions;
    }

    @GetMapping("/api/auctions/suggest")
    public ApiResponse<SearchSuggestions.Suggestions> suggest(@RequestParam(defaultValue = "") String q) {
        return ApiResponse.of(suggestions.suggest(q));
    }

    @GetMapping("/api/auctions/{id}/price-history")
    public ApiResponse<SearchSuggestions.PriceHistory> priceHistory(@PathVariable UUID id) {
        auctions.requirePubliclyVisible(id);
        return ApiResponse.of(suggestions.history(id, Instant.now()));
    }
}
