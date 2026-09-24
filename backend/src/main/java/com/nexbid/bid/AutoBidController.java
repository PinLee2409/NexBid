package com.nexbid.bid;

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

import com.nexbid.bid.dto.AutoBidRequest;
import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;

/**
 * EN: Auto bid endpoints (guide §31). Everything is about the caller's own auto bid; nobody can read
 *     or change anyone else's.
 * VI: Các endpoint auto bid (guide §31). Mọi thứ chỉ xoay quanh auto bid của chính người gọi; không ai đọc
 *     hay sửa được của người khác.
 */
@RestController
@RequestMapping("/api/auctions/{auctionId}/auto-bid")
public class AutoBidController {

    private final AutoBidService autoBids;

    public AutoBidController(AutoBidService autoBids) {
        this.autoBids = autoBids;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AutoBidView> create(
            @AuthenticationPrincipal CurrentUser me,
            @PathVariable UUID auctionId,
            @Valid @RequestBody AutoBidRequest request) {

        return ApiResponse.of(autoBids.create(me.id(), auctionId, request.maxAmount()), "Auto bid set");
    }

    @PutMapping
    public ApiResponse<AutoBidView> update(
            @AuthenticationPrincipal CurrentUser me,
            @PathVariable UUID auctionId,
            @Valid @RequestBody AutoBidRequest request) {

        return ApiResponse.of(autoBids.update(me.id(), auctionId, request.maxAmount()), "Auto bid updated");
    }

    @DeleteMapping
    public ApiResponse<AutoBidView> cancel(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID auctionId) {
        return ApiResponse.of(autoBids.cancel(me.id(), auctionId), "Auto bid cancelled");
    }

    /**
     * EN: Not in the spec's list, but the lot page cannot show "your max" after a reload without it.
     * VI: Không có trong danh sách của spec, nhưng thiếu nó thì trang lô không hiện được "mức tối đa của bạn"
     *     sau khi tải lại.
     */
    @GetMapping
    public ApiResponse<AutoBidView> mine(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID auctionId) {
        return ApiResponse.of(autoBids.mine(me.id(), auctionId));
    }
}
