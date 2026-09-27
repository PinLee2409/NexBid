package com.nexbid.order;

import java.util.Arrays;
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
import com.nexbid.order.repository.OrderRepository;
import com.nexbid.payment.PaymentService;
import com.nexbid.payment.PaymentView;

/**
 * EN: Orders (guide §33). Opening, paying and cancelling an unpaid order follow the payment; after that the
 *     seller ships, the buyer confirms receipt, and an admin may refund (spec §5.3).
 * VI: Đơn hàng (guide §33). Mở đơn, trả tiền và huỷ đơn chưa trả đi theo khoản thanh toán; sau đó người bán
 *     gửi hàng, người mua xác nhận đã nhận, và admin có thể hoàn tiền (spec §5.3).
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final PaymentService payments;
    private final AuctionService auctions;
    private final ApplicationEventPublisher events;

    public OrderService(
            OrderRepository orders, PaymentService payments, AuctionService auctions, ApplicationEventPublisher events) {
        this.orders = orders;
        this.payments = payments;
        this.auctions = auctions;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<OrderView> listFor(UUID buyerId) {
        List<Order> mine = orders.findByBuyerIdOrderByCreatedAtDesc(buyerId);
        return views(mine, payments.detailsFor(buyerId, paymentIdsOf(mine)));
    }

    /**
     * EN: Someone else's order answers exactly like one that does not exist.
     * VI: Đơn của người khác trả lời y như đơn không tồn tại.
     */
    @Transactional(readOnly = true)
    public OrderView get(UUID buyerId, UUID orderId) {
        Order order = orders.findByIdAndBuyerId(orderId, buyerId).orElseThrow(() -> notFound(orderId));
        return views(List.of(order), payments.detailsFor(buyerId, List.of(order.getPaymentId()))).getFirst();
    }

    /** EN: What a seller has sold, newest first. / VI: Những gì người bán đã bán, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<OrderView> listForSeller(UUID sellerId) {
        List<Order> sold = orders.findBySellerIdOrderByCreatedAtDesc(sellerId);
        return views(sold, payments.detailsOf(paymentIdsOf(sold)));
    }

    /**
     * EN: Every order in the given statuses (all of them when none is given), most recently changed first.
     * VI: Mọi đơn theo các trạng thái đã cho (tất cả nếu không truyền), đơn vừa thay đổi gần nhất lên trước.
     */
    @Transactional(readOnly = true)
    public PageView<OrderView> listForAdmin(List<OrderStatus> statuses, int page, int size) {
        List<OrderStatus> wanted = statuses == null || statuses.isEmpty() ? Arrays.asList(OrderStatus.values()) : statuses;
        Page<Order> found = orders.findByStatusIn(wanted, PageRequest.of(
                Math.max(0, page - 1), Math.clamp(size, 1, 100), Sort.by(Sort.Direction.DESC, "updatedAt")));

        Map<UUID, OrderView> byId = views(found.getContent(), payments.detailsOf(paymentIdsOf(found.getContent())))
                .stream()
                .collect(Collectors.toMap(view -> view.order().id(), Function.identity()));
        return PageView.of(found, order -> byId.get(order.getId()));
    }

    /**
     * EN: The seller marks a paid order as shipped. Another seller's order answers 404.
     * VI: Người bán đánh dấu một đơn đã trả là đã gửi hàng. Đơn của người bán khác trả 404.
     */
    @Transactional
    public OrderView ship(UUID sellerId, UUID orderId) {
        Order order = orders.findLockedById(orderId)
                .filter(found -> found.getSellerId().equals(sellerId))
                .orElseThrow(() -> notFound(orderId));

        move(order, OrderStatus.PAID, OrderStatus.PROCESSING, "Only a paid order can be shipped");
        events.publishEvent(new OrderEvents.Shipped(order.getId(), sellerId));
        return views(List.of(order), payments.detailsOf(List.of(order.getPaymentId()))).getFirst();
    }

    /**
     * EN: The buyer confirms the goods arrived. Another buyer's order answers 404.
     * VI: Người mua xác nhận đã nhận hàng. Đơn của người mua khác trả 404.
     */
    @Transactional
    public OrderView confirmReceipt(UUID buyerId, UUID orderId) {
        Order order = orders.findLockedById(orderId)
                .filter(found -> found.getBuyerId().equals(buyerId))
                .orElseThrow(() -> notFound(orderId));

        move(order, OrderStatus.PROCESSING, OrderStatus.COMPLETED, "Only a shipped order can be confirmed as received");
        events.publishEvent(new OrderEvents.Received(order.getId(), buyerId));
        return views(List.of(order), payments.detailsOf(List.of(order.getPaymentId()))).getFirst();
    }

    /**
     * EN: An admin gives the money back: the order is cancelled and its payment REFUNDED, in one transaction.
     *     Only while the goods are not confirmed received.
     * VI: Admin hoàn tiền: đơn bị huỷ và khoản thanh toán thành REFUNDED, trong cùng một transaction. Chỉ khi
     *     hàng chưa được xác nhận đã nhận.
     */
    @Transactional
    public OrderView refund(UUID adminId, UUID orderId) {
        Order order = orders.findLockedById(orderId).orElseThrow(() -> notFound(orderId));
        if (!order.getStatus().isRefundable()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "Only a paid order that has not been received can be refunded");
        }

        order.moveTo(OrderStatus.CANCELLED);
        orders.save(order);
        payments.refund(order.getPaymentId(), adminId);
        return views(List.of(order), payments.detailsOf(List.of(order.getPaymentId()))).getFirst();
    }

    private void move(Order order, OrderStatus from, OrderStatus to, String refusal) {
        if (order.getStatus() != from) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, refusal);
        }
        order.moveTo(to);
        orders.save(order);
    }

    private List<OrderView> views(List<Order> list, Map<UUID, PaymentView.Details> paid) {
        Map<UUID, AuctionSummaryView> lots = auctions.participantSummariesOf(
                list.stream().map(Order::getAuctionId).toList());

        return list.stream()
                .map(order -> new OrderView(
                        new OrderView.Details(
                                order.getId(),
                                order.getAuctionId(),
                                order.getBuyerId(),
                                order.getSellerId(),
                                order.getPaymentId(),
                                order.getAmount(),
                                order.getStatus(),
                                order.getCreatedAt(),
                                order.getUpdatedAt()),
                        paid.get(order.getPaymentId()),
                        lots.get(order.getAuctionId())))
                .toList();
    }

    private static List<UUID> paymentIdsOf(List<Order> list) {
        return list.stream().map(Order::getPaymentId).toList();
    }

    private static ResourceNotFoundException notFound(UUID orderId) {
        return new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND, "No order with id " + orderId);
    }
}
