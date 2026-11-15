package com.nexbid.auction;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nexbid.auction.dto.AuctionQuery;
import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;

/**
 * EN: The public catalogue (guide §18). No token: someone deciding whether to join has to see what is on
 *     offer first.
 * VI: Danh mục công khai (guide §18). Không cần token: người đang cân nhắc tham gia phải xem được có gì trước.
 */
@RestController
@RequestMapping("/api/auctions")
public class AuctionController {

    private final AuctionService auctions;
    private final LotInsights insights;

    public AuctionController(AuctionService auctions, LotInsights insights) {
        this.auctions = auctions;
        this.insights = insights;
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

    /**
     * EN: The visitor's browser reports a visit (the page itself is rendered on the server, which would count as one
     *     visitor). Counted once per viewer per day.
     * VI: Trình duyệt của người xem báo một lượt xem (trang được dựng ở server, nếu đếm ở đó thì chỉ là một người xem).
     *     Mỗi người xem được đếm một lần mỗi ngày.
     */
    @PostMapping("/{id}/views")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void viewed(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser viewer, HttpServletRequest http) {
        auctions.requirePubliclyVisible(id);
        insights.recordView(id, viewer == null ? null : viewer.id(), http.getRemoteAddr(), Instant.now());
    }

    /**
     * EN: Cards for lots the visitor saw recently (kept in their browser), in the order given; hidden lots are skipped.
     * VI: Thẻ của các lô người xem đã xem gần đây (lưu ở trình duyệt của họ), đúng thứ tự gửi lên; lô bị ẩn bị bỏ qua.
     */
    @GetMapping("/cards")
    public ApiResponse<List<AuctionSummaryView>> cards(@RequestParam List<UUID> ids) {
        return ApiResponse.of(auctions.publicSummariesOf(ids.stream().distinct().limit(12).toList()));
    }

    /** EN: Open lots like this one, for the foot of its page. / VI: Các lô đang mở giống lô này, cho cuối trang của nó. */
    @GetMapping("/{id}/similar")
    public ApiResponse<List<AuctionSummaryView>> similar(@PathVariable UUID id) {
        return ApiResponse.of(insights.similarTo(id));
    }
}
