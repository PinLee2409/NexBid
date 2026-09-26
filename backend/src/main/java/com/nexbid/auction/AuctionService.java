package com.nexbid.auction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;
    private final LotCache cards;

    AuctionService(
            AuctionRepository auctions,
            ProductService products,
            ProductImageService images,
            UserService users,
            ApplicationEventPublisher events,
            LotCache cards) {
        this.auctions = auctions;
        this.products = products;
        this.images = images;
        this.users = users;
        this.events = events;
        this.cards = cards;
    }

    /**
     * EN: Opens every lot whose start time has arrived (guide §25). Capped per run so one large backlog
     *     cannot hold thousands of row locks inside a single transaction; the next run takes the rest.
     * VI: Mở mọi lô đã tới giờ (guide §25). Giới hạn mỗi lượt chạy để một đống tồn đọng không giữ hàng
     *     nghìn khoá dòng trong một transaction; lượt chạy sau sẽ xử lý phần còn lại.
     */
    @Transactional
    public int startDueAuctions(Instant now, int batchSize) {
        List<Auction> due = auctions.findDueToStart(now, PageRequest.of(0, batchSize));

        for (Auction auction : due) {
            auction.setStatus(AuctionStatus.ACTIVE);
            events.publishEvent(AuctionLifecycleEvent.started(auction));
        }

        auctions.saveAll(due);
        return due.size();
    }

    /**
     * EN: Closes every lot whose end time has arrived (guide §26) and names its winner (guide §27). The
     *     price and bid count stay exactly as the last accepted bid set them — that price is the final one.
     * VI: Đóng mọi lô đã tới giờ kết thúc (guide §26) và chỉ định người thắng (guide §27). Giá và số lượt
     *     giữ nguyên như lượt trả giá cuối cùng đã đặt — mức giá đó chính là giá chốt.
     */
    @Transactional
    public int endDueAuctions(Instant now, int batchSize) {
        List<Auction> due = auctions.findDueToEnd(now, PageRequest.of(0, batchSize));

        for (Auction auction : due) {
            // EN: Winner chosen inside the same lock that closed the bidding (guide §27, spec §12).
            // VI: Người thắng được chọn trong cùng khoá đã đóng việc trả giá (guide §27, spec §12).
            auction.close();

            if (auction.getWinnerId() == null) {
                // EN: Nothing was sold, so the product is the seller's to list again.
                // VI: Chưa bán được gì, nên sản phẩm trở lại tay người bán để đăng lại.
                products.markAuctionState(auction.getProductId(), ProductStatus.AVAILABLE);
            }

            events.publishEvent(AuctionLifecycleEvent.ended(auction));
        }

        auctions.saveAll(due);
        return due.size();
    }

    /**
     * EN: The winner let the payment deadline pass (spec §17). No sale happened, so the lot is cancelled and
     *     the product goes back to the seller — otherwise it would stay locked to a buyer who never paid.
     * VI: Người thắng để quá hạn thanh toán (spec §17). Giao dịch không thành, nên lô bị huỷ và sản phẩm trở
     *     về tay người bán — nếu không nó sẽ bị khoá mãi cho một người mua không bao giờ trả tiền.
     */
    @Transactional
    public void cancelUnpaid(UUID auctionId) {
        Auction auction = auctions.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.AUCTION_NOT_FOUND, "No auction with id " + auctionId));

        if (auction.getStatus() != AuctionStatus.ENDED) {
            return;
        }

        auction.setStatus(AuctionStatus.CANCELLED);
        products.markAuctionState(auction.getProductId(), ProductStatus.AVAILABLE);
        auctions.save(auction);
    }

    /**
     * EN: The winner paid (guide §33): the lot is COMPLETED and the product SOLD. Called inside the payment's
     *     own transaction, so "paid" and "completed" can never disagree.
     * VI: Người thắng đã trả (guide §33): lô chuyển COMPLETED và sản phẩm SOLD. Được gọi trong chính transaction
     *     thanh toán, nên "đã trả" và "đã hoàn tất" không bao giờ lệch nhau.
     */
    @Transactional
    public void completeSale(UUID auctionId) {
        Auction auction = auctions.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.AUCTION_NOT_FOUND, "No auction with id " + auctionId));

        if (auction.getStatus() != AuctionStatus.ENDED) {
            return;
        }

        auction.setStatus(AuctionStatus.COMPLETED);
        products.markAuctionState(auction.getProductId(), ProductStatus.SOLD);
        auctions.save(auction);
    }

    public UUID sellerOf(UUID auctionId) {
        return auctions.findById(auctionId)
                .map(Auction::getSellerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.AUCTION_NOT_FOUND, "No auction with id " + auctionId));
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

        if (auctions.holdsProduct(product.id())) {
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
            Auction saved = auctions.save(auction);
            events.publishEvent(new AuctionAuditEvent(
                    AuctionAuditEvent.Action.CREATED, saved.getId(), sellerId, null, saved.getStatus()));
            return toView(saved);
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

        return PageView.of(page, summarize(page.getContent(), AuctionService::toPublicView));
    }

    /**
     * EN: Lots the caller won (guide §27), most recent first. Paid ones stay on the list — winning did
     *     not stop being true because the bill was settled.
     * VI: Các lô người gọi đã thắng (guide §27), mới nhất trước. Lô đã thanh toán vẫn nằm trong danh sách —
     *     đã thắng thì vẫn là đã thắng, dù hoá đơn đã trả xong.
     */
    public List<AuctionSummaryView> winsOf(UUID userId) {
        List<Auction> won = auctions.findByWinnerIdAndStatusInOrderByEndTimeDesc(
                userId, List.of(AuctionStatus.ENDED, AuctionStatus.COMPLETED));

        return won.stream().map(summarize(won, AuctionService::toView)).toList();
    }

    /**
     * EN: Takes the bidding lock and reports where the lot stands (guide §31). Auto bidding holds this
     *     lock for its whole decision, so no manual bid can slip in between reading and answering.
     * VI: Giữ khoá trả giá và cho biết lô đang ở đâu (guide §31). Việc trả giá tự động giữ khoá này suốt
     *     lúc quyết định, nên không lượt trả giá thủ công nào chen vào giữa lúc đọc và lúc đáp trả.
     */
    @Transactional
    public BiddingState lockBiddingState(UUID auctionId, Instant at) {
        return stateOf(auctions.findByIdForUpdate(auctionId), auctionId, at);
    }

    /** EN: The same, without the lock, for reads that decide nothing. / VI: Như trên nhưng không khoá, cho các lần đọc không quyết định gì. */
    public BiddingState biddingState(UUID auctionId, Instant at) {
        return stateOf(auctions.findById(auctionId), auctionId, at);
    }

    private static BiddingState stateOf(Optional<Auction> found, UUID auctionId, Instant at) {
        Auction auction = found.orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.AUCTION_NOT_FOUND, "No auction with id " + auctionId));

        return new BiddingState(
                auction.getId(),
                auction.getSellerId(),
                AuctionRules.isOpenForBidding(auction, at),
                hasEnded(auction, at),
                auction.getCurrentPrice(),
                auction.getMinimumIncrement(),
                AuctionRules.minimumNextBid(auction),
                auction.getBidCount(),
                auction.getLeadingBidderId(),
                auction.getEndTime());
    }

    private static boolean hasEnded(Auction auction, Instant at) {
        return auction.getStatus() == AuctionStatus.ENDED
                || auction.getStatus() == AuctionStatus.COMPLETED
                || !at.isBefore(auction.getEndTime());
    }

    /**
     * EN: What a lot is called, for text written about it elsewhere — a notification, later an email.
     * VI: Tên gọi của một lô, cho những đoạn chữ viết về nó ở nơi khác — thông báo, sau này là email.
     */
    public String lotTitleOf(UUID auctionId) {
        return auctions.findById(auctionId)
                .flatMap(auction -> products.findById(auction.getProductId()))
                .map(ProductView::name)
                .orElse("an auction");
    }

    /**
     * EN: Scheduled lots opening in (from, to]. For heads-up notices; the window's lower bound is open so
     *     a lot is never counted as "about to open" once it has.
     * VI: Các lô đã lên lịch sẽ mở trong khoảng (from, to]. Dùng cho thông báo nhắc trước; cận dưới mở để
     *     một lô đã mở rồi không bao giờ bị tính là "sắp mở".
     */
    public List<UUID> openingBetween(Instant from, Instant to) {
        return auctions.findIdsOpeningBetween(from, to);
    }

    /** EN: Running lots closing in (from, to]. / VI: Các lô đang chạy sẽ đóng trong khoảng (from, to]. */
    public List<UUID> closingBetween(Instant from, Instant to) {
        return auctions.findIdsClosingBetween(from, to);
    }

    /**
     * EN: Catalogue cards for the given lots, in the order given, skipping any that are not on public
     *     view. For other modules that keep their own lists of lots, such as the watchlist.
     * VI: Thẻ danh mục cho các lô được đưa vào, giữ đúng thứ tự, bỏ qua lô nào không công khai. Dành cho
     *     các module tự giữ danh sách lô của riêng mình, như danh sách theo dõi.
     */
    public List<AuctionSummaryView> publicSummariesOf(List<UUID> auctionIds) {
        if (auctionIds.isEmpty()) {
            return List.of();
        }

        var byId = auctions.findAllById(auctionIds).stream()
                .filter(auction -> PUBLIC_STATUSES.contains(auction.getStatus()))
                .collect(Collectors.toMap(Auction::getId, auction -> auction));

        List<Auction> visible = auctionIds.stream().map(byId::get).filter(Objects::nonNull).toList();

        return visible.stream().map(summarize(visible, AuctionService::toPublicView)).toList();
    }

    /**
     * EN: Cards for lots the caller took part in, whatever their status now — a winner still needs to see
     *     the lot behind a payment after it was cancelled. Only ever call it with such lots.
     * VI: Thẻ cho các lô mà người gọi có tham gia, bất kể trạng thái hiện tại — người thắng vẫn cần thấy lô
     *     đứng sau một khoản thanh toán kể cả khi lô đã bị huỷ. Chỉ gọi với những lô như vậy.
     */
    public Map<UUID, AuctionSummaryView> participantSummariesOf(List<UUID> auctionIds) {
        if (auctionIds.isEmpty()) {
            return Map.of();
        }

        List<Auction> lots = auctions.findAllById(auctionIds);
        return lots.stream()
                .map(summarize(lots, AuctionService::toPublicView))
                .collect(Collectors.toMap(card -> card.auction().id(), card -> card));
    }

    /**
     * EN: Builds catalogue cards. Products, covers and seller names are fetched once for all the lots
     *     rather than per row.
     * VI: Dựng các thẻ danh mục. Sản phẩm, ảnh bìa và tên người bán lấy một lần cho tất cả các lô thay vì
     *     từng dòng một.
     */
    private Function<Auction, AuctionSummaryView> summarize(
            List<Auction> lots, Function<Auction, AuctionView> view) {

        // EN: The card is cached; the auction itself (price, bids, status, clock) is always read fresh.
        // VI: Thẻ được cache; bản thân phiên (giá, lượt, trạng thái, đồng hồ) luôn đọc mới.
        Map<UUID, LotCard> cardsById = cards.cardsFor(lots);

        return auction -> {
            LotCard card = cardsById.get(auction.getId());

            return new AuctionSummaryView(
                    view.apply(auction),
                    new AuctionSummaryView.Product(
                            auction.getProductId(),
                            card == null ? "Unknown item" : card.name(),
                            card == null ? null : card.coverImageUrl()),
                    card == null ? null : card.category(),
                    new AuctionSummaryView.Seller(
                            auction.getSellerId(),
                            card == null ? "Unknown seller" : card.sellerName()));
        };
    }

    /**
     * EN: Applies a bid to a lot (guide §20). Every rule the guide lists is checked here, in its order,
     *     and the auction is the only thing that moves — the bid record itself belongs to the bid module.
     * VI: Áp một lượt trả giá vào lô (guide §20). Mọi luật guide liệt kê đều kiểm ở đây theo đúng thứ tự,
     *     và chỉ phiên đấu giá thay đổi — bản ghi lượt trả giá thuộc về module bid.
     */
    public AcceptedBid acceptBid(UUID auctionId, UUID bidderId, BigDecimal amount) {
        return acceptBid(auctionId, bidderId, amount, Instant.now());
    }

    /**
     * EN: The same, judged at a given instant. An auto bid answering a bid is judged at that bid's instant,
     *     so it can never fail on a deadline the bid it answers had already beaten.
     * VI: Như trên, nhưng xét tại một thời điểm cho trước. Auto bid đáp trả một lượt được xét tại đúng thời
     *     điểm của lượt đó, nên không bao giờ hỏng vì một hạn chót mà lượt nó đáp trả đã kịp vượt qua.
     */
    @Transactional
    public AcceptedBid acceptBid(UUID auctionId, UUID bidderId, BigDecimal amount, Instant at) {
        // EN: Locked, not merely read (guide §21). Reading the price and writing the new one must be one
        //     indivisible step, or two bidders both read 10m and both think they won at 10.5m.
        // VI: Đọc kèm khoá chứ không chỉ đọc (guide §21). Đọc giá rồi ghi giá mới phải là một bước không
        //     thể tách, nếu không hai người cùng đọc 10 triệu và cùng tưởng mình thắng ở 10,5 triệu.
        Auction auction = auctions.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.AUCTION_NOT_FOUND, "No auction with id " + auctionId));

        Instant now = at;

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

        // EN: Read before it is overwritten — whoever led until now is the one who has just been outbid.
        // VI: Đọc trước khi bị ghi đè — ai dẫn tới lúc này chính là người vừa bị vượt giá.
        UUID previousLeader = auction.getLeadingBidderId();

        auction.applyBid(bidderId, amount);

        // EN: Judged at the same instant the bid was accepted, so "15 seconds left" means exactly that.
        // VI: Xét tại đúng thời điểm lượt trả giá được nhận, nên "còn 15 giây" nghĩa là đúng 15 giây.
        if (AuctionRules.isLastMinuteBid(auction, now)) {
            auction.extendForLastMinuteBid();
            events.publishEvent(AuctionLifecycleEvent.extended(auction));
        }

        auctions.save(auction);

        return new AcceptedBid(
                auction.getId(),
                auction.getCurrentPrice(),
                auction.getBidCount(),
                AuctionRules.minimumNextBid(auction),
                auction.getEndTime(),
                now,
                previousLeader);
    }

    /** EN: What the caller learns once a bid is in. / VI: Những gì bên gọi biết được sau khi lượt trả giá vào. */
    public record AcceptedBid(
            UUID auctionId,
            BigDecimal currentPrice,
            int bidCount,
            BigDecimal minimumNextBid,
            Instant endTime,
            // EN: The instant the bid was checked against the clock; the bid record carries exactly this.
            // VI: Thời điểm lượt trả giá được so với đồng hồ; bản ghi lượt trả giá mang đúng mốc này.
            Instant acceptedAt,
            // EN: Who led before this bid; null for the first bid. May be the bidder themselves.
            // VI: Ai dẫn trước lượt này; null nếu là lượt đầu. Có thể chính là người vừa trả giá.
            UUID previousLeaderId) {
    }

    /**
     * EN: One lot for the public page (guide §19, spec §7.7). A lot that has not been approved answers the
     *     same as one that does not exist — the browse list hides it, so the direct URL must too.
     * VI: Một lô cho trang công khai (guide §19, spec §7.7). Lô chưa được duyệt trả lời y như lô không tồn
     *     tại — danh sách duyệt hàng đã giấu nó, nên gọi thẳng URL cũng phải giấu.
     */
    /**
     * EN: Fails exactly as the public page does when a lot is not on show. Anything hanging off a lot —
     *     its bid history, later its watchers — has to hide behind the same door, or the door is decoration.
     * VI: Báo lỗi y hệt trang công khai khi lô không được trưng ra. Mọi thứ gắn theo một lô — lịch sử trả
     *     giá, sau này là người theo dõi — đều phải nấp sau cùng một cánh cửa, nếu không cửa chỉ để trang trí.
     */
    public void requirePubliclyVisible(UUID auctionId) {
        auctions.findById(auctionId)
                .filter(candidate -> PUBLIC_STATUSES.contains(candidate.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.AUCTION_NOT_FOUND, "No auction with id " + auctionId));
    }

    public AuctionDetailPublicView getPublic(UUID auctionId) {
        Auction auction = auctions.findById(auctionId)
                .filter(candidate -> PUBLIC_STATUSES.contains(candidate.getStatus()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.AUCTION_NOT_FOUND, "No auction with id " + auctionId));

        // EN: The card (product, photos, seller) may come from Redis; the auction row above is always fresh,
        //     and the two time-dependent fields below are worked out per request, never cached.
        // VI: Thẻ (sản phẩm, ảnh, người bán) có thể lấy từ Redis; dòng auction ở trên luôn mới, và hai trường
        //     phụ thuộc thời gian bên dưới được tính cho từng request, không bao giờ cache.
        LotCard card = cards.cardsFor(List.of(auction)).get(auction.getId());
        if (card == null) {
            throw new ResourceNotFoundException(
                    ErrorCode.PRODUCT_NOT_FOUND, "The product behind this auction is gone");
        }

        Instant now = Instant.now();

        return new AuctionDetailPublicView(
                toPublicView(auction),
                new AuctionDetailPublicView.Product(card.productId(), card.name(), card.description(), card.condition()),
                card.category(),
                card.images(),
                new AuctionDetailPublicView.Seller(auction.getSellerId(), card.sellerName()),
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
    public AuctionView approve(UUID auctionId, UUID adminId) {
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

        Auction saved = auctions.save(auction);
        events.publishEvent(new AuctionAuditEvent(
                AuctionAuditEvent.Action.APPROVED, auctionId, adminId,
                AuctionStatus.PENDING_APPROVAL, saved.getStatus()));
        return toView(saved);
    }

    /**
     * EN: Refuses a lot with a reason (guide §17). The product stays free, so the seller can fix what was
     *     wrong and list it again.
     * VI: Từ chối một lô kèm lý do (guide §17). Sản phẩm vẫn tự do, nên người bán sửa chỗ sai rồi đăng lại được.
     */
    @Transactional
    public AuctionView reject(UUID auctionId, UUID adminId, String reason) {
        Auction auction = pendingAuction(auctionId);

        auction.setStatus(AuctionStatus.REJECTED);
        auction.setRejectionReason(reason.trim());

        Auction saved = auctions.save(auction);
        events.publishEvent(new AuctionAuditEvent(
                AuctionAuditEvent.Action.REJECTED, auctionId, adminId,
                AuctionStatus.PENDING_APPROVAL, saved.getStatus()));
        return toView(saved);
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
        return toView(auction, auction.getWinnerId());
    }

    /**
     * EN: The same lot for strangers, minus the winner's account id. The bid history masks every name;
     *     a raw id published beside the result would undo that with one lookup.
     * VI: Cùng lô đó cho người lạ xem, bỏ id tài khoản của người thắng. Lịch sử trả giá che mọi cái tên;
     *     một id gốc công bố cạnh kết quả sẽ phá bỏ điều đó chỉ bằng một lần tra.
     */
    private static AuctionView toPublicView(Auction auction) {
        return toView(auction, null);
    }

    private static AuctionView toView(Auction auction, UUID winnerShown) {
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
                winnerShown,
                auction.getRejectionReason(),
                auction.getCreatedAt(),
                auction.getUpdatedAt());
    }
}
