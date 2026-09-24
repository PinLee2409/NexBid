package com.nexbid.order;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.order.entity.Order;
import com.nexbid.order.repository.OrderRepository;
import com.nexbid.payment.PaymentService;
import com.nexbid.payment.PaymentView;

/**
 * EN: The buyer's orders (guide §33). Reading only — orders are opened, paid and cancelled by what happens
 *     to their payment, never by a request.
 * VI: Đơn hàng của người mua (guide §33). Chỉ để đọc — đơn được mở, trả và huỷ theo những gì xảy ra với
 *     khoản thanh toán của nó, không bao giờ bởi một request.
 */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final PaymentService payments;
    private final AuctionService auctions;

    public OrderService(OrderRepository orders, PaymentService payments, AuctionService auctions) {
        this.orders = orders;
        this.payments = payments;
        this.auctions = auctions;
    }

    @Transactional(readOnly = true)
    public List<OrderView> listFor(UUID buyerId) {
        return views(buyerId, orders.findByBuyerIdOrderByCreatedAtDesc(buyerId));
    }

    /**
     * EN: Someone else's order answers exactly like one that does not exist.
     * VI: Đơn của người khác trả lời y như đơn không tồn tại.
     */
    @Transactional(readOnly = true)
    public OrderView get(UUID buyerId, UUID orderId) {
        Order order = orders.findByIdAndBuyerId(orderId, buyerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.ORDER_NOT_FOUND, "No order with id " + orderId));

        return views(buyerId, List.of(order)).getFirst();
    }

    private List<OrderView> views(UUID buyerId, List<Order> mine) {
        Map<UUID, PaymentView.Details> paid = payments.detailsFor(
                buyerId, mine.stream().map(Order::getPaymentId).toList());
        Map<UUID, AuctionSummaryView> lots = auctions.participantSummariesOf(
                mine.stream().map(Order::getAuctionId).toList());

        return mine.stream()
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
}
