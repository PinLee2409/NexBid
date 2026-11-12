package com.nexbid.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.auction.PageView;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.order.entity.Order;
import com.nexbid.order.entity.Review;
import com.nexbid.order.repository.OrderRepository;
import com.nexbid.order.repository.ReviewRepository;
import com.nexbid.user.UserService;

/**
 * EN: Buyers rate sellers: once per order, and only once the buyer has confirmed the goods arrived — a review of a
 *     sale that has not finished would be a guess.
 * VI: Người mua đánh giá người bán: mỗi đơn một lần, và chỉ sau khi người mua đã xác nhận nhận hàng — đánh giá một
 *     giao dịch chưa xong thì chỉ là phỏng đoán.
 */
@Service
public class ReviewService {

    /** EN: Orders that count as a sale: paid, and not refunded. / VI: Đơn được tính là đã bán: đã trả tiền, không bị hoàn. */
    private static final List<OrderStatus> SOLD = List.of(OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.COMPLETED);

    private final OrderRepository orders;
    private final ReviewRepository reviews;
    private final UserService users;
    private final AuctionService auctions;
    private final ApplicationEventPublisher events;

    ReviewService(
            OrderRepository orders,
            ReviewRepository reviews,
            UserService users,
            AuctionService auctions,
            ApplicationEventPublisher events) {
        this.orders = orders;
        this.reviews = reviews;
        this.users = users;
        this.auctions = auctions;
        this.events = events;
    }

    /**
     * EN: The order row is the lock, so a double click leaves one review. Another buyer's order answers 404.
     * VI: Dòng đơn hàng là khoá, nên bấm hai lần chỉ để lại một đánh giá. Đơn của người mua khác trả 404.
     */
    @Transactional
    public ReviewView review(UUID buyerId, UUID orderId, int rating, String comment) {
        Order order = orders.findLockedById(orderId)
                .filter(found -> found.getBuyerId().equals(buyerId))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND, "No order with id " + orderId));
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED, "Only a received order can be reviewed");
        }
        if (reviews.existsByOrderId(orderId)) {
            throw new BusinessException(ErrorCode.REVIEW_EXISTS, "This order has already been reviewed");
        }

        String text = comment == null || comment.isBlank() ? null : comment.trim();
        Review saved = reviews.saveAndFlush(new Review(order, rating, text, Instant.now()));
        events.publishEvent(new OrderEvents.Reviewed(saved.getId(), orderId, order.getAuctionId(),
                order.getSellerId(), buyerId, rating, saved.getCreatedAt()));
        return views(List.of(saved)).getFirst();
    }

    /** EN: Public: a seller's score and a page of reviews. / VI: Công khai: điểm của người bán và một trang đánh giá. */
    @Transactional(readOnly = true)
    public SellerReviewsView ofSeller(UUID sellerId, int page, int size) {
        ReviewRepository.Score score = reviews.scoreOf(sellerId);
        long sales = orders.countBySellerIdAndStatusIn(sellerId, SOLD);
        Page<Review> found = reviews.findBySellerId(sellerId, PageRequest.of(
                Math.max(0, page - 1), Math.clamp(size, 1, 50), Sort.by(Sort.Direction.DESC, "createdAt")));

        Map<UUID, ReviewView> byId = views(found.getContent()).stream()
                .collect(Collectors.toMap(ReviewView::id, Function.identity()));
        BigDecimal average = score.getAverage() == null
                ? null
                : BigDecimal.valueOf(score.getAverage()).setScale(1, RoundingMode.HALF_UP);
        return new SellerReviewsView(average, score.getCount(), sales, PageView.of(found, review -> byId.get(review.getId())));
    }

    /** EN: The reviews behind a list of orders, for order pages. / VI: Các đánh giá của một danh sách đơn, cho trang đơn hàng. */
    Map<UUID, ReviewView> forOrders(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return views(reviews.findByOrderIdIn(orderIds)).stream()
                .collect(Collectors.toMap(ReviewView::orderId, Function.identity()));
    }

    private List<ReviewView> views(List<Review> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = users.namesOf(list.stream().map(Review::getBuyerId).distinct().toList());
        Map<UUID, AuctionSummaryView> lots = auctions.participantSummariesOf(
                list.stream().map(Review::getAuctionId).distinct().toList());
        return list.stream().map(review -> new ReviewView(
                review.getId(),
                review.getOrderId(),
                review.getAuctionId(),
                review.getRating(),
                review.getComment(),
                UserService.handleOf(names.get(review.getBuyerId())),
                lots.containsKey(review.getAuctionId()) ? lots.get(review.getAuctionId()).product().name() : null,
                review.getCreatedAt())).toList();
    }
}
