package com.nexbid.bid;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * EN: Taking one's bids back, and the staff log of who did. The log sits under the moderators' paths.
 * VI: Rút lại các lượt trả giá của mình, và nhật ký cho nhân viên xem ai đã rút. Nhật ký nằm dưới đường dẫn của kiểm duyệt.
 */
@RestController
public class BidRetractionController {

    private final BidRetractions retractions;

    BidRetractionController(BidRetractions retractions) {
        this.retractions = retractions;
    }

    public record RetractRequest(@NotNull BidRetractions.Reason reason,
            @Size(max = 300, message = "Note must be at most 300 characters") String note) {
    }

    @GetMapping("/api/auctions/{auctionId}/bids/retraction")
    public ApiResponse<BidRetractions.Eligibility> eligibility(@AuthenticationPrincipal CurrentUser me,
            @PathVariable UUID auctionId) {
        return ApiResponse.of(retractions.check(auctionId, me.id(), Instant.now()));
    }

    @PostMapping("/api/auctions/{auctionId}/bids/retract")
    public ApiResponse<BidRetractions.Retracted> retract(@AuthenticationPrincipal CurrentUser me,
            @PathVariable UUID auctionId, @Valid @RequestBody RetractRequest request) {
        return ApiResponse.of(retractions.retract(auctionId, me.id(), request.reason(), request.note(), Instant.now()),
                "Bids taken back");
    }

    @GetMapping("/api/admin/bid-retractions")
    public ApiResponse<List<BidRetractions.LogEntry>> log(@RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.of(retractions.log(limit, Instant.now()));
    }
}
