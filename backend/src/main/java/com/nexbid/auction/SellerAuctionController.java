package com.nexbid.auction;

import java.util.List;
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

import com.nexbid.auction.dto.CreateAuctionRequest;
import com.nexbid.auction.dto.ReserveDecisionRequest;
import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;

/**
 * EN: A seller's own auctions (guide §14). SELLER comes from the /api/seller/** pattern set at function 06.
 * VI: Phiên đấu giá của chính người bán (guide §14). Quyền SELLER đến từ mẫu /api/seller/** đặt ở chức năng 06.
 */
@RestController
@RequestMapping("/api/seller/auctions")
public class SellerAuctionController {

    private final AuctionService auctions;
    private final LotInsights insights;
    private final LotImport lotImport;
    private LotBatch batch;
    private PriceHints priceHints;

    @org.springframework.beans.factory.annotation.Autowired
    void setPriceHints(PriceHints priceHints) {
        this.priceHints = priceHints;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void setBatch(LotBatch batch) {
        this.batch = batch;
    }

    public SellerAuctionController(AuctionService auctions, LotInsights insights, LotImport lotImport) {
        this.auctions = auctions;
        this.insights = insights;
        this.lotImport = lotImport;
    }

    /**
     * EN: Many lots at once from a CSV file and their photos; each row becomes a draft lot, or a reason it could not.
     * VI: Nhiều lô cùng lúc từ một file CSV kèm ảnh; mỗi dòng thành một lô nháp, hoặc lý do không tạo được.
     */
    @PostMapping(value = "/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<LotImport.Result> importLots(@AuthenticationPrincipal CurrentUser seller,
            @org.springframework.web.bind.annotation.RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @org.springframework.web.bind.annotation.RequestPart(name = "images", required = false)
            List<org.springframework.web.multipart.MultipartFile> images) {
        return ApiResponse.of(lotImport.run(seller.id(), file, images));
    }

    /** EN: A starting price to suggest, from what similar lots sold for. / VI: Giá khởi điểm gợi ý, từ giá bán của các lô tương tự. */
    @GetMapping("/price-hint")
    public ApiResponse<PriceHints.Hint> priceHint(@AuthenticationPrincipal CurrentUser seller,
            @org.springframework.web.bind.annotation.RequestParam UUID productId) {
        return ApiResponse.of(priceHints.forProduct(seller.id(), productId, java.time.Instant.now()));
    }

    public record BatchRequest(@jakarta.validation.constraints.NotEmpty List<UUID> auctionIds,
            java.time.Instant startTime, java.time.Instant endTime, java.time.Instant submitAt, Boolean submit) {
    }

    /**
     * EN: Many drafts at once: the same times for all, and approval now, at a set time, or not yet.
     * VI: Nhiều bản nháp một lúc: cùng giờ cho tất cả, và gửi duyệt ngay, hẹn giờ, hoặc chưa gửi.
     */
    @PostMapping("/batch")
    public ApiResponse<List<LotBatch.Row>> batch(@AuthenticationPrincipal CurrentUser seller,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody BatchRequest request) {
        return ApiResponse.of(batch.apply(seller.id(), new LotBatch.Request(request.auctionIds(), request.startTime(),
                request.endTime(), request.submitAt(), Boolean.TRUE.equals(request.submit())), java.time.Instant.now()));
    }

    /** EN: Views, watchers, bids per lot, and how many closed lots sold. / VI: Lượt xem, theo dõi, trả giá từng lô, và tỉ lệ lô đã đóng bán được. */
    @GetMapping("/stats")
    public ApiResponse<LotInsights.SellerStats> stats(@AuthenticationPrincipal CurrentUser seller) {
        return ApiResponse.of(insights.forSeller(seller.id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuctionView> create(
            @AuthenticationPrincipal CurrentUser seller,
            @Valid @RequestBody CreateAuctionRequest request) {

        return ApiResponse.of(auctions.create(seller.id(), request), "Auction created as a draft");
    }

    @GetMapping
    public ApiResponse<List<AuctionView>> mine(@AuthenticationPrincipal CurrentUser seller) {
        return ApiResponse.of(auctions.listOwnedBy(seller.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<AuctionView> one(
            @AuthenticationPrincipal CurrentUser seller, @PathVariable UUID id) {

        return ApiResponse.of(auctions.getOwned(seller.id(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<AuctionView> update(
            @AuthenticationPrincipal CurrentUser seller,
            @PathVariable UUID id,
            @Valid @RequestBody CreateAuctionRequest request) {

        return ApiResponse.of(auctions.update(seller.id(), id, request), "Auction updated");
    }

    /**
     * EN: Hands the lot to an admin (guide §15). After this the seller waits.
     * VI: Giao lô cho admin (guide §15). Sau bước này người bán chỉ còn việc chờ.
     */
    public record RelistRequest(@jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(50) Integer dropPercent) {
    }

    /** EN: A new draft from a lot that closed unsold. / VI: Bản nháp mới từ một lô đã đóng mà không bán được. */
    @PostMapping("/{id}/relist")
    public ApiResponse<AuctionView> relist(@AuthenticationPrincipal CurrentUser seller, @PathVariable UUID id,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody(required = false) RelistRequest request) {
        int drop = request == null || request.dropPercent() == null ? 0 : request.dropPercent();
        return ApiResponse.of(auctions.relistAsDraft(seller.id(), id, drop), "Draft created");
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<AuctionView> submit(
            @AuthenticationPrincipal CurrentUser seller, @PathVariable UUID id) {

        return ApiResponse.of(auctions.submitForApproval(seller.id(), id), "Sent for approval");
    }

    /**
     * EN: Sell anyway, or not, a lot that closed below its reserve. / VI: Vẫn bán, hay không, một lô đóng dưới giá sàn.
     */
    @PostMapping("/{id}/reserve-decision")
    public ApiResponse<AuctionView> decideReserve(
            @AuthenticationPrincipal CurrentUser seller,
            @PathVariable UUID id,
            @Valid @RequestBody ReserveDecisionRequest request) {
        return ApiResponse.of(auctions.decideReserve(seller.id(), id, request.accept()),
                request.accept() ? "Sold to the highest bidder" : "Not sold");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> cancelDraft(
            @AuthenticationPrincipal CurrentUser seller, @PathVariable UUID id) {

        auctions.cancelDraft(seller.id(), id);
        return ApiResponse.ok("Draft auction removed");
    }
}
