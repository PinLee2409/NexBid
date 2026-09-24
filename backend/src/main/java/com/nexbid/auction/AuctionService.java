package com.nexbid.auction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.dto.CreateAuctionRequest;
import com.nexbid.auction.entity.Auction;
import com.nexbid.auction.repository.AuctionRepository;
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
