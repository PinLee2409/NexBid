package com.nexbid.auction;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

/**
 * EN: The lots the caller won (guide §27). The identity comes from the token only — there is no way to
 *     ask about anyone else's wins.
 * VI: Các lô người gọi đã thắng (guide §27). Danh tính chỉ lấy từ token — không có cách nào hỏi về lô
 *     thắng của người khác.
 */
@RestController
public class MyWinsController {

    private final AuctionService auctions;

    public MyWinsController(AuctionService auctions) {
        this.auctions = auctions;
    }

    @GetMapping("/api/users/me/wins")
    public ApiResponse<List<AuctionSummaryView>> wins(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(auctions.winsOf(me.id()));
    }
}
