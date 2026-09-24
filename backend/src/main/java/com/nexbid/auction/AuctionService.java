package com.nexbid.auction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.dto.AuctionQuery;
import com.nexbid.auction.dto.CreateAuctionRequest;
import com.nexbid.auction.entity.Auction;
import com.nexbid.auction.repository.AuctionRepository;
import com.nexbid.auction.repository.AuctionSpecifications;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.product.ProductImageService;
import com.nexbid.product.ProductService;
import com.nexbid.product.ProductStatus;
import com.nexbid.product.ProductView;
import com.nexbid.user.UserService;

/**
 * EN: Creating and editing a lot before it opens (guide §14, spec §7.4).
 * VI: Tạo và sửa một lô trước khi nó mở (guide §14, spec §7.4).
 */
@Service
public class AuctionService {

    /** EN: Statuses that still tie up the product. / VI: Các trạng thái vẫn còn giữ chỗ sản phẩm. */
    private static final List<AuctionStatus> LIVE = List.of(
            AuctionStatus.DRAFT,
            AuctionStatus.PENDING_APPROVAL,
            AuctionStatus.SCHEDULED,
            AuctionStatus.ACTIVE,
            AuctionStatus.ENDED);

    /** EN: "Ending soon" means within the hour. / VI: "Sắp đóng" nghĩa là trong vòng một giờ. */
    private static final long ENDING_SOON_SECONDS = 3600;

    /**
     * EN: What a visitor may see, whether they browse or arrive on a direct link.
     * VI: Những gì khách được xem, dù họ duyệt danh sách hay vào thẳng bằng đường dẫn.
     */
    private static final List<AuctionStatus> PUBLIC_STATUSES = List.of(
            AuctionStatus.SCHEDULED,
            AuctionStatus.ACTIVE,
            AuctionStatus.ENDED,
            AuctionStatus.COMPLETED);

    private final AuctionRepository auctions;
    private final ProductService products;
    private final ProductImageService images;
    private final UserService users;

    public AuctionService(
            AuctionRepository auctions,
            ProductService products,
            ProductImageService images,
            UserService users) {
        this.auctions = auctions;
        this.products = products;
        this.images = images;
        this.users = users;
    }

    @Transactional
    public AuctionView create(UUID sellerId, CreateAuctionRequest request) {
        // EN: Throws 404 when the product is someone else's, which also satisfies "the product must be
        //     the seller's" without telling a stranger that the id is real.
        // VI: Ném 404 khi sản phẩm là của người khác — vừa thoả luật "sản phẩm phải của người bán",
        //     vừa không tiết lộ cho người lạ biết id đó có thật.
        ProductView product = products.getOwned(sellerId, request.productId());

        requireSellableProduct(product);
        requireSaneSchedule(request.startTime(), request.endTime());

        if (auctions.existsByProductIdAndStatusIn(product.id(), LIVE)) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_ALREADY_IN_AUCTION,
                    "This product already has an auction that has not finished");
        }

        Auction auction = new Auction(
                product.id(),
                sellerId,
                request.startingPrice(),
                request.minimumIncrement(),
                request.startTime(),
                request.endTime(),
                Boolean.TRUE.equals(request.antiSnipingEnabled()),
                request.antiSnipingWindowSeconds() == null ? 30 : request.antiSnipingWindowSeconds(),
                request.extensionSeconds() == null ? 120 : request.extensionSeconds());

        try {
            return toView(auctions.save(auction));
        } catch (DataIntegrityViolationException ex) {
            // EN: Two requests can both pass the check above. The partial unique index is the real guard.
            // VI: Hai request đều có thể qua được bước kiểm trên. Index duy nhất có điều kiện mới là chốt thật.
            throw new BusinessException(
                    ErrorCode.PRODUCT_ALREADY_IN_AUCTION,
                    "This product already has an auction that has not finished");
        }
    }

    public List<AuctionView> listOwnedBy(UUID sellerId) {
        return auctions.findBySellerIdOrderByCreatedAtDesc(sellerId).stream()
                .map(AuctionService::toView)
                .toList();
    }

    public AuctionView getOwned(UUID sellerId, UUID auctionId) {
        return auctions.findByIdAndSellerId(auctionId, sellerId)
                .map(AuctionService::toView)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.AUCTION_NOT_FOUND, "No auction with id " + auctionId));
    }

    /**
     * EN: Edits a draft. Spec §7.4 is explicit: once it has been sent for approval the seller cannot
     *     change it directly — otherwise the terms an admin approved would not be the terms that run.
     * VI: Sửa bản nháp. Spec §7.4 nói rõ: đã gửi duyệt thì người bán không được sửa trực tiếp — nếu không,
     *     điều khoản admin duyệt sẽ khác điều khoản thực sự chạy.
     */
    @Transactional
    public AuctionView update(UUID sellerId, UUID auctionId, CreateAuctionRequest request) {
        Auction auction = auctions.findByIdAndSellerId(auctionId, sellerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.AUCTION_NOT_FOUND, "No auction with id " + auctionId));

        if (!auction.getStatus().isEditableBySeller()) {
            throw new BusinessException(
                    ErrorCode.AUCTION_NOT_EDITABLE,
                    "Only a draft auction can be changed");
        }

        requireSaneSchedule(request.startTime(), request.endTime());

        auction.setStartingPrice(request.startingPrice());
        auction.setMinimumIncrement(request.minimumIncrement());
        auction.setStartTime(request.startTime());
        auction.setEndTime(request.endTime());
        auction.setAntiSnipingEnabled(Boolean.TRUE.equals(request.antiSnipingEnabled()));

        if (request.antiSnipingWindowSeconds() != null) {
            auction.setAntiSnipingWindowSeconds(request.antiSnipingWindowSeconds());
        }
        if (request.extensionSeconds() != null) {
            auction.setExtensionSeconds(request.extensionSeconds());
        }

        return toView(auctions.save(auction));
    }

    /**
     * EN: A draft can be abandoned, which frees the product to be listed again.
     * VI: Bản nháp có thể bỏ đi, và sản phẩm được giải phóng để đăng lại.
     */
    @Transactional
    public void cancelDraft(UUID sellerId, UUID auctionId) {
        Auction auction = auctions.findByIdAndSellerId(auctionId, sellerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.AUCTION_NOT_FOUND, "No auction with id " + auctionId));

        if (!auction.getStatus().isEditableBySeller()) {
            throw new BusinessException(
                    ErrorCode.AUCTION_NOT_EDITABLE, "Only a draft auction can be cancelled this way");
        }

        auctions.delete(auction);
    }

    /**
     * EN: The public browse list (guide §18, spec §7.6). Nothing that has not been approved appears here.
     * VI: Danh sách duyệt hàng công khai (guide §18, spec §7.6). Thứ gì chưa được duyệt thì không xuất hiện.
     */
    public PageView<AuctionSummaryView> browse(AuctionQuery query) {
        boolean filterByCategory = query.category() != null && !query.category().isEmpty();

        Specification<Auction> spec = AuctionSpecifications.allOf(
                AuctionSpecifications.publiclyVisible(),
                AuctionSpecifications.statusIn(query.status()),
                AuctionSpecifications.priceAtLeast(query.minPrice()),
                AuctionSpecifications.priceAtMost(query.maxPrice()),
                AuctionSpecifications.endingSoon(
                        Boolean.TRUE.equals(query.endingSoon()), Instant.now(), ENDING_SOON_SECONDS),
                filterByCategory
                        ? AuctionSpecifications.productIn(products.idsInCategorySlugs(query.category()))
                        : null);

        var page = auctions.findAll(
                spec, PageRequest.of(query.zeroBasedPage(), query.size(), sortOf(query.sort())));

        // EN: Products, covers and seller names are fetched once for the whole page rather than per row.
        // VI: Sản phẩm, ảnh bìa và tên người bán lấy một lần cho cả trang thay vì từng dòng một.
        List<UUID> productIds = page.getContent().stream().map(Auction::getProductId).toList();
        var productsById = products.findAllById(productIds);
        var coversByProduct = images.coversOf(productIds);
        var sellerNames = users.namesOf(page.getContent().stream().map(Auction::getSellerId).toList());

        return PageView.of(page, auction -> {
            ProductView product = productsById.get(auction.getProductId());

            return new AuctionSummaryView(
                    toView(auction),
                    new AuctionSummaryView.Product(
                            auction.getProductId(),
                            product == null ? "Unknown item" : product.name(),
                            coversByProduct.get(auction.getProductId())),
                    product == null ? null : product.category(),
                    new AuctionSummaryView.Seller(
                            auction.getSellerId(),
                            sellerNames.getOrDefault(auction.getSellerId(), "Unknown seller")));
        });
    }

    /**
     * EN: Applies a bid to a lot (guide §20). Every rule the guide lists is checked here, in its order,
     *     and the auction is the only thing that moves — the bid record itself belongs to the bid module.
     * VI: Áp một lượt trả giá vào lô (guide §20). Mọi luật guide liệt kê đều kiểm ở đây theo đúng thứ tự,
     *     và chỉ phiên đấu giá thay đổi — bản ghi lượt trả giá thuộc về module bid.
     */
    @Transactional
    public AcceptedBid acceptBid(UUID auctionId, UUID bidderId, BigDecimal amount) {
        // EN: Locked, not merely read (guide §21). Reading the price and writing the new one must be one
        //     indivisible step, or two bidders both read 10m and both think they won at 10.5m.
        // VI: Đọc kèm khoá chứ không chỉ đọc (guide §21). Đọc giá rồi ghi giá mới phải là một bước không
        //     thể tách, nếu không hai người cùng đọc 10 triệu và cùng tưởng mình thắng ở 10,5 triệu.
        Auction auction = auctions.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.AUCTION_NOT_FOUND, "No auction with id " + auctionId));

        Instant now = Instant.now();

        // EN: Ended first, so a lot whose clock ran out says so rather than "not active".
        // VI: Kiểm hết giờ trước, để lô đã hết thời gian nói đúng điều đó thay vì "chưa mở".
        if (auction.getStatus() == AuctionStatus.ENDED
                || auction.getStatus() == AuctionStatus.COMPLETED
                || !now.isBefore(auction.getEndTime())) {

            throw new BusinessException(
                    ErrorCode.AUCTION_ALREADY_ENDED, "This auction has already ended");
        }

        if (!AuctionRules.isOpenForBidding(auction, now)) {
            throw new BusinessException(
                    ErrorCode.AUCTION_NOT_ACTIVE, "This auction is not open for bidding");
        }

        // EN: A seller bidding on their own lot is how a price gets pushed up with nobody paying it.
        // VI: Người bán tự trả giá lô của mình chính là cách đẩy giá lên mà không ai phải trả.
        if (auction.isOwnedBy(bidderId)) {
            throw new BusinessException(
                    ErrorCode.SELLER_CANNOT_BID, "You cannot bid on your own auction");
        }

        BigDecimal minimum = AuctionRules.minimumNextBid(auction);
        if (amount.compareTo(minimum) < 0) {
            throw new BusinessException(
                    ErrorCode.BID_TOO_LOW,
                    "The bid must be at least " + minimum.toPlainString());
        }

        auction.applyBid(amount);
        auctions.save(auction);

        return new AcceptedBid(
                auction.getId(),
                auction.getCurrentPrice(),
                auction.getBidCount(),
                AuctionRules.minimumNextBid(auction),
                auction.getEndTime());
    }

    /** EN: What the caller learns once a bid is in. / VI: Những gì bên gọi biết được sau khi lượt trả giá vào. */
    public record AcceptedBid(
            UUID auctionId,
            BigDecimal currentPrice,
            int bidCount,
            BigDecimal minimumNextBid,
            Instant endTime) {
    }

    /**
     * EN: One lot for the public page (guide §19, spec §7.7). A lot that has not been approved answers the
     *     same as one that does not exist — the browse list hides it, so the direct URL must too.
     * VI: Một lô cho trang công khai (guide §19, spec §7.7). Lô chưa được duyệt trả lời y như lô không tồn
     *     tại — danh sách duyệt hàng đã giấu nó, nên gọi thẳng URL cũng phải giấu.
     */
    public AuctionDetailPublicView getPublic(UUID auctionId) {
        Auction auction = auctions.findById(auctionId)
                .filter(candidate -> PUBLIC_STATUSES.contains(candidate.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.AUCTION_NOT_FOUND, "No auction with id " + auctionId));

        ProductView product = products.findById(auction.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PRODUCT_NOT_FOUND, "The product behind this auction is gone"));

        String sellerName = users.findById(auction.getSellerId())
                .map(account -> account.fullName())
                .orElse("Unknown seller");

        Instant now = Instant.now();

        return new AuctionDetailPublicView(
                toView(auction),
                new AuctionDetailPublicView.Product(
                        product.id(),
                        product.name(),
                        product.description(),
                        product.condition().name()),
                product.category(),
                images.listPublic(product.id()),
                new AuctionDetailPublicView.Seller(auction.getSellerId(), sellerName),
                AuctionRules.minimumNextBid(auction),
                AuctionRules.isOpenForBidding(auction, now),
                now);
    }

    /**
     * EN: Newest and ending-soon both need a tiebreak, or two lots with the same timestamp can swap places
     *     between pages and one of them is never seen.
     * VI: Sắp theo mới nhất và sắp đóng đều cần tiêu chí phụ, nếu không hai lô cùng mốc thời gian có thể đổi
     *     chỗ giữa các trang và một trong hai không bao giờ được nhìn thấy.
     */
    private static Sort sortOf(AuctionSort sort) {
        return switch (sort) {
            case NEWEST -> Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
            case ENDING_SOON -> Sort.by(Sort.Order.asc("endTime"), Sort.Order.asc("id"));
            case PRICE_ASC -> Sort.by(Sort.Order.asc("currentPrice"), Sort.Order.asc("id"));
            case PRICE_DESC -> Sort.by(Sort.Order.desc("currentPrice"), Sort.Order.asc("id"));
            case MOST_BIDS -> Sort.by(Sort.Order.desc("bidCount"), Sort.Order.asc("id"));
        };
    }

    /**
     * EN: Sends a draft to an admin (guide §15). From here the seller cannot change it — the terms an
     *     admin approves have to be the terms that run.
     * VI: Gửi bản nháp cho admin (guide §15). Từ đây người bán không sửa được nữa — điều khoản admin duyệt
     *     phải chính là điều khoản sẽ chạy.
     */
    @Transactional
    public AuctionView submitForApproval(UUID sellerId, UUID auctionId) {
        Auction auction = auctions.findByIdAndSellerId(auctionId, sellerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.AUCTION_NOT_FOUND, "No auction with id " + auctionId));

        if (auction.getStatus() != AuctionStatus.DRAFT) {
            throw new BusinessException(
                    ErrorCode.AUCTION_NOT_EDITABLE, "Only a draft auction can be submitted");
        }

        // EN: Checked again on the way out. A draft may have sat for days while its end time went past.
        // VI: Kiểm lại lần nữa lúc gửi đi. Bản nháp có thể nằm đó nhiều ngày và mốc kết thúc đã trôi qua.
        requireSaneSchedule(auction.getStartTime(), auction.getEndTime());

        auction.setStatus(AuctionStatus.PENDING_APPROVAL);
        return toView(auctions.save(auction));
    }

    /** EN: Waiting for a decision (guide §16). / VI: Đang chờ quyết định (guide §16). */
    public List<AuctionView> listPendingApproval() {
        return auctions.findByStatusOrderByCreatedAtAsc(AuctionStatus.PENDING_APPROVAL).stream()
                .map(AuctionService::toView)
                .toList();
    }

    /**
     * EN: One lot with everything a reviewer needs (guide §16).
     * VI: Một lô kèm mọi thứ người duyệt cần (guide §16).
     */
    public AuctionDetailView getForReview(UUID auctionId) {
        Auction auction = auctions.findById(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.AUCTION_NOT_FOUND, "No auction with id " + auctionId));

        ProductView product = products.findById(auction.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PRODUCT_NOT_FOUND, "The product behind this auction is gone"));

        String sellerName = users.findById(auction.getSellerId())
                .map(account -> account.fullName())
                .orElse("Unknown seller");

        return new AuctionDetailView(
                toView(auction),
                product,
                images.listPublic(product.id()),
                new AuctionDetailView.Seller(auction.getSellerId(), sellerName));
    }

    /**
     * EN: Approves a lot (guide §17, spec §7.5). Where it lands depends on the clock: a start time still
     *     ahead means SCHEDULED, one already passed means the bidding opens immediately.
     * VI: Duyệt một lô (guide §17, spec §7.5). Nó rơi vào đâu tuỳ theo đồng hồ: giờ mở còn ở phía trước thì
     *     SCHEDULED, đã qua rồi thì mở nhận trả giá ngay.
     */
    @Transactional
    public AuctionView approve(UUID auctionId) {
        Auction auction = pendingAuction(auctionId);
        Instant now = Instant.now();

        // EN: Not in the guide, but approving a lot whose clock already ran out would create an auction
        //     that can never take a bid.
        // VI: Guide không nói, nhưng duyệt một lô đã hết giờ sẽ tạo ra phiên không bao giờ nhận được
        //     lượt trả giá nào.
        if (!auction.getEndTime().isAfter(now)) {
            throw new BusinessException(
                    ErrorCode.AUCTION_SCHEDULE_INVALID,
                    "This auction's end time has already passed; the seller must reschedule it");
        }

        auction.setStatus(auction.getStartTime().isAfter(now)
                ? AuctionStatus.SCHEDULED
                : AuctionStatus.ACTIVE);

        // EN: Approval is the moment the product stops being the seller's to edit or delete.
        // VI: Duyệt xong là lúc sản phẩm không còn thuộc quyền sửa hay xoá của người bán nữa.
        products.markAuctionState(auction.getProductId(), ProductStatus.IN_AUCTION);

        return toView(auctions.save(auction));
    }

    /**
     * EN: Refuses a lot with a reason (guide §17). The product stays free, so the seller can fix what was
     *     wrong and list it again.
     * VI: Từ chối một lô kèm lý do (guide §17). Sản phẩm vẫn tự do, nên người bán sửa chỗ sai rồi đăng lại được.
     */
    @Transactional
    public AuctionView reject(UUID auctionId, String reason) {
        Auction auction = pendingAuction(auctionId);

        auction.setStatus(AuctionStatus.REJECTED);
        auction.setRejectionReason(reason.trim());

        return toView(auctions.save(auction));
    }

    /**
     * EN: Only a lot that is actually waiting can be decided on. Anything else is a stale screen or a
     *     second reviewer arriving after the first.
     * VI: Chỉ lô thật sự đang chờ mới quyết được. Ngoài ra là màn hình cũ chưa làm mới, hoặc người duyệt
     *     thứ hai tới sau người thứ nhất.
     */
    private Auction pendingAuction(UUID auctionId) {
        Auction auction = auctions.findById(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.AUCTION_NOT_FOUND, "No auction with id " + auctionId));

        if (auction.getStatus() != AuctionStatus.PENDING_APPROVAL) {
            throw new BusinessException(
                    ErrorCode.AUCTION_NOT_PENDING,
                    "This auction is not waiting for approval");
        }

        return auction;
    }

    /**
     * EN: Not in the guide's list, but auctioning a half-written listing makes no sense, and a sold item
     *     cannot be sold twice.
     * VI: Không có trong danh sách của guide, nhưng đem một tin bán hàng viết dở lên sàn thì vô nghĩa,
     *     và món đã bán không thể bán lần hai.
     */
    private static void requireSellableProduct(ProductView product) {
        if (product.status() != ProductStatus.AVAILABLE) {
            throw new BusinessException(
                    ErrorCode.PRODUCT_NOT_SELLABLE,
                    "Only a published, unsold product can be put up for auction");
        }
    }

    private static void requireSaneSchedule(Instant startTime, Instant endTime) {
        // EN: Spec §7.4, and the database repeats it as a CHECK constraint.
        // VI: Spec §7.4, và database nhắc lại bằng một ràng buộc CHECK.
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(
                    ErrorCode.AUCTION_SCHEDULE_INVALID, "End time must be after start time");
        }

        // EN: Not in the guide either, but a lot that closed before it was written can never run.
        // VI: Cũng không có trong guide, nhưng một lô đóng trước cả lúc được viết ra thì không bao giờ chạy được.
        if (!endTime.isAfter(Instant.now())) {
            throw new BusinessException(
                    ErrorCode.AUCTION_SCHEDULE_INVALID, "End time must be in the future");
        }
    }

    private static AuctionView toView(Auction auction) {
        return new AuctionView(
                auction.getId(),
                auction.getProductId(),
                auction.getSellerId(),
                auction.getStartingPrice(),
                auction.getCurrentPrice(),
                auction.getMinimumIncrement(),
                auction.getStartTime(),
                auction.getEndTime(),
                auction.getStatus(),
                new AuctionView.AntiSniping(
                        auction.isAntiSnipingEnabled(),
                        auction.getAntiSnipingWindowSeconds(),
                        auction.getExtensionSeconds()),
                auction.getExtensionCount(),
                auction.getBidCount(),
                auction.getWinnerId(),
                auction.getRejectionReason(),
                auction.getCreatedAt(),
                auction.getUpdatedAt());
    }
}
