package com.nexbid.auction;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import com.nexbid.auction.dto.AuctionQuery;
import com.nexbid.common.response.ApiResponse;

/**
 * EN: The public catalogue (guide §18). No token: someone deciding whether to join has to see what is on
 *     offer first.
 * VI: Danh mục công khai (guide §18). Không cần token: người đang cân nhắc tham gia phải xem được có gì trước.
 */
@RestController
@RequestMapping("/api/auctions")
public class AuctionController {

    private final AuctionService auctions;

    public AuctionController(AuctionService auctions) {
        this.auctions = auctions;
    }

    @GetMapping
    public ApiResponse<PageView<AuctionSummaryView>> browse(AuctionQuery query) {
        return ApiResponse.of(auctions.browse(query));
    }

    /**
     * EN: One lot, with everything the page draws and the server time its countdown measures against.
     * VI: Một lô, kèm mọi thứ trang cần vẽ và mốc giờ server mà đồng hồ đếm ngược đo theo.
     */
    @GetMapping("/{id}")
    public ApiResponse<AuctionDetailPublicView> one(@PathVariable UUID id) {
        return ApiResponse.of(auctions.getPublic(id));
    }
}
