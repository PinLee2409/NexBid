package com.nexbid.order;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
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
import com.nexbid.user.BankAccount;
import com.nexbid.user.BankAccounts;

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
    private final ReviewService reviews;
    private final ApplicationEventPublisher events;
    private final DisputeBook disputes;
    private final Duration autoCompleteAfter;
    private final BankAccounts bankAccounts;

    public OrderService(
            OrderRepository orders,
            PaymentService payments,
            AuctionService auctions,
            ReviewService reviews,
            ApplicationEventPublisher events,
            DisputeBook disputes,
            @Value("${nexbid.order.auto-complete-after}") Duration autoCompleteAfter,
            BankAccounts bankAccounts) {
        this.bankAccounts = bankAccounts;
        this.orders = orders;
        this.payments = payments;
        this.auctions = auctions;
        this.reviews = reviews;
        this.events = events;
        this.disputes = disputes;
        this.autoCompleteAfter = autoCompleteAfter;
    }

    /** EN: A shipped order nobody confirmed yet, and when it completes on its own. / VI: Một đơn đã gửi chưa ai xác nhận, và lúc nó tự hoàn tất. */
    public record AwaitingReceipt(UUID orderId, UUID auctionId, UUID buyerId, Instant completesAt) {
    }

    /**
     * EN: Shipped orders that will complete on their own between {@code from} and {@code to}, for the buyer's reminder.
     * VI: Các đơn đã gửi sẽ tự hoàn tất trong khoảng {@code from} tới {@code to}, để nhắc người mua.
     */
    @Transactional(readOnly = true)
    public List<AwaitingReceipt> completingBetween(Instant from, Instant to) {
        return orders.findByStatusAndShippedAtBetween(OrderStatus.PROCESSING,
                        from.minus(autoCompleteAfter), to.minus(autoCompleteAfter)).stream()
                .map(order -> new AwaitingReceipt(order.getId(), order.getAuctionId(), order.getBuyerId(),
                        order.getShippedAt().plus(autoCompleteAfter)))
                .toList();
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
    public OrderView ship(UUID sellerId, UUID orderId, String carrier, String trackingNumber) {
        Order order = orders.findLockedById(orderId)
                .filter(found -> found.getSellerId().equals(sellerId))
                .orElseThrow(() -> notFound(orderId));

        move(order, OrderStatus.PAID, OrderStatus.PROCESSING, "Only a paid order can be shipped");
        order.recordShipment(blankToNull(carrier), blankToNull(trackingNumber), Instant.now());
        events.publishEvent(new OrderEvents.Shipped(order.getId(), sellerId, order.getBuyerId(), order.getAuctionId(),
                order.getCarrier(), order.getTrackingNumber()));
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

        if (order.getStatus() != OrderStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID, "Only a shipped order can be confirmed as received");
        }
        complete(order, buyerId, false, Instant.now());
        return views(List.of(order), payments.detailsOf(List.of(order.getPaymentId()))).getFirst();
    }

    /**
     * EN: Shipped orders nobody confirmed and nobody disputed complete on their own once {@code shippedBefore} has
     *     passed, so a forgetful buyer cannot keep the seller's money waiting forever. Returns how many completed.
     * VI: Đơn đã gửi mà không ai xác nhận và không ai khiếu nại sẽ tự hoàn tất sau {@code shippedBefore}, để người mua
     *     quên bấm không giữ tiền của người bán mãi được. Trả về số đơn đã hoàn tất.
     */
    @Transactional
    public int completeOverdue(Instant shippedBefore, int limit) {
        List<Order> overdue = orders.lockShippedBefore(shippedBefore, limit);
        Instant now = Instant.now();
        overdue.forEach(order -> complete(order, null, true, now));
        return overdue.size();
    }

    private void complete(Order order, UUID actorId, boolean automatic, Instant at) {
        OrderStatus from = order.getStatus();
        order.complete(at, automatic);
        orders.save(order);
        events.publishEvent(new OrderEvents.Received(order.getId(), actorId, from));
        events.publishEvent(new OrderEvents.Completed(order.getId(), order.getAuctionId(), order.getSellerId(),
                order.getBuyerId(), order.getAmount(), automatic, at));
    }

    /**
     * EN: The buyer reports a problem with a paid order, shipped or not. The order waits as DISPUTED — it cannot
     *     complete on its own — until an admin decides.
     * VI: Người mua báo có vấn đề với một đơn đã trả, đã gửi hay chưa. Đơn chờ ở trạng thái DISPUTED — không tự hoàn
     *     tất được — cho tới khi admin quyết định.
     */
    @Transactional
    public OrderView openDispute(UUID buyerId, UUID orderId, String reason, String details) {
        Order order = orders.findLockedById(orderId)
                .filter(found -> found.getBuyerId().equals(buyerId))
                .orElseThrow(() -> notFound(orderId));
        if (order.getStatus() != OrderStatus.PAID && order.getStatus() != OrderStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "Only a paid order that has not been completed can be disputed");
        }

        Instant now = Instant.now();
        DisputeBook.Reason why = DisputeBook.Reason.valueOf(reason);
        UUID disputeId = disputes.open(orderId, buyerId, why, details.trim(), order.getStatus(), now);
        order.moveTo(OrderStatus.DISPUTED);
        orders.save(order);
        events.publishEvent(new OrderEvents.Disputed(disputeId, orderId, order.getAuctionId(), buyerId,
                order.getSellerId(), why.name(), now));
        return views(List.of(order), payments.detailsOf(List.of(order.getPaymentId()))).getFirst();
    }

    /** EN: A dispute next to its order, for the admin queue. / VI: Một khiếu nại cạnh đơn của nó, cho hàng chờ của admin. */
    public record DisputeEntry(OrderView.Dispute dispute, OrderView entry) {
    }

    @Transactional(readOnly = true)
    public List<DisputeEntry> disputes(String status) {
        List<DisputeBook.Row> rows = disputes.byStatus(DisputeBook.Status.valueOf(status), 200);
        List<Order> found = orders.findAllById(rows.stream().map(DisputeBook.Row::orderId).toList());
        Map<UUID, OrderView> byOrder = views(found, payments.detailsOf(paymentIdsOf(found))).stream()
                .collect(Collectors.toMap(view -> view.order().id(), Function.identity()));
        return rows.stream().map(row -> new DisputeEntry(row.view(), byOrder.get(row.orderId()))).toList();
    }

    /**
     * EN: An admin decides. Refund: the admin has transferred the money to the buyer's bank account, gives the bank's
     *     reference, and the order is cancelled. Reject: a shipped order completes — the admin judged the goods arrived
     *     as described — and an unshipped one goes back to waiting for the seller.
     * VI: Admin quyết định. Hoàn tiền: admin đã chuyển tiền vào tài khoản ngân hàng của người mua, nhập mã giao dịch, và
     *     đơn bị huỷ. Bác: đơn đã gửi thì hoàn tất — admin xét là hàng đã tới đúng mô tả — còn đơn chưa gửi thì quay lại
     *     chờ người bán.
     */
    @Transactional
    public OrderView resolveDispute(UUID adminId, UUID disputeId, boolean refund, String note, String reference) {
        if (refund && (reference == null || reference.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "A refund needs the bank transfer's reference");
        }
        DisputeBook.Row dispute = disputes.lockById(disputeId).orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.DISPUTE_NOT_FOUND, "No dispute with id " + disputeId));
        if (dispute.status() != DisputeBook.Status.OPEN) {
            throw new BusinessException(ErrorCode.DISPUTE_CLOSED, "This dispute has already been decided");
        }
        Order order = orders.findLockedById(dispute.orderId()).orElseThrow(() -> notFound(dispute.orderId()));
        Instant now = Instant.now();
        String text = note == null || note.isBlank() ? null : note.trim();

        if (refund) {
            cancelAndRefund(order, adminId, reference);
        } else if (dispute.priorStatus() == OrderStatus.PROCESSING) {
            complete(order, adminId, false, now);
        } else {
            order.moveTo(dispute.priorStatus());
            orders.save(order);
        }
        disputes.resolve(disputeId, refund ? DisputeBook.Status.REFUNDED : DisputeBook.Status.REJECTED, text, adminId, now);
        events.publishEvent(new OrderEvents.DisputeResolved(disputeId, order.getId(), order.getAuctionId(),
                order.getBuyerId(), order.getSellerId(), refund, text, adminId, now));
        return views(List.of(order), payments.detailsOf(List.of(order.getPaymentId()))).getFirst();
    }

    /**
     * EN: Where the buyer of an order wants a refund sent, for the admin about to make the transfer; null if they have
     *     not said. Only an admin sees it: the seller's view of an order never carries it.
     * VI: Nơi người mua của đơn muốn nhận tiền hoàn, cho admin sắp chuyển khoản; null nếu họ chưa cho biết. Chỉ admin
     *     xem được: góc nhìn đơn hàng của người bán không bao giờ có nó.
     */
    @Transactional(readOnly = true)
    public BankAccount refundAccountOf(UUID orderId) {
        Order order = orders.findById(orderId).orElseThrow(() -> notFound(orderId));
        return bankAccounts.of(order.getBuyerId()).orElse(null);
    }

    /**
     * EN: An admin has transferred the money back to the buyer's bank account and records it with the bank's
     *     reference: the order is cancelled and its payment REFUNDED, in one transaction. Only while the goods are not
     *     confirmed received.
     * VI: Admin đã chuyển trả tiền vào tài khoản ngân hàng của người mua và ghi nhận kèm mã giao dịch: đơn bị huỷ và
     *     khoản thanh toán thành REFUNDED, trong cùng một transaction. Chỉ khi hàng chưa được xác nhận đã nhận.
     */
    @Transactional
    public OrderView refund(UUID adminId, UUID orderId, String reference) {
        Order order = orders.findLockedById(orderId).orElseThrow(() -> notFound(orderId));
        if (!order.getStatus().isRefundable()) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID,
                    "Only a paid order that has not been received can be refunded");
        }

        // EN: Refunding a disputed order settles its dispute too. / VI: Hoàn tiền một đơn đang khiếu nại cũng là chốt khiếu nại đó.
        java.util.Optional<DisputeBook.Row> open = disputes.openFor(orderId);
        cancelAndRefund(order, adminId, reference);
        open.ifPresent(dispute -> {
            Instant now = Instant.now();
            disputes.resolve(dispute.id(), DisputeBook.Status.REFUNDED, null, adminId, now);
            events.publishEvent(new OrderEvents.DisputeResolved(dispute.id(), order.getId(), order.getAuctionId(),
                    order.getBuyerId(), order.getSellerId(), true, null, adminId, now));
        });
        return views(List.of(order), payments.detailsOf(List.of(order.getPaymentId()))).getFirst();
    }

    /** EN: The buyer must have said where the money goes. / VI: Người mua phải cho biết tiền chuyển về đâu. */
    private void cancelAndRefund(Order order, UUID adminId, String reference) {
        BankAccount account = bankAccounts.of(order.getBuyerId()).orElseThrow(() -> new BusinessException(
                ErrorCode.REFUND_ACCOUNT_REQUIRED, "The buyer has not added a bank account for refunds yet"));
        order.moveTo(OrderStatus.CANCELLED);
        orders.save(order);
        payments.refund(order.getPaymentId(), adminId, reference.trim(), account);
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
        Map<UUID, ReviewView> reviewed = reviews.forOrders(list.stream().map(Order::getId).toList());
        Map<UUID, DisputeBook.Row> disputed = disputes.latestFor(list.stream().map(Order::getId).toList());

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
                                order.getUpdatedAt(),
                                order.getShipAddress() == null ? null : new OrderView.Shipping(
                                        order.getShipRecipient(), order.getShipPhone(), order.getShipAddress()),
                                order.getCarrier(),
                                order.getTrackingNumber(),
                                order.getShippedAt(),
                                order.getCompletedAt(),
                                order.isAutoCompleted(),
                                disputed.containsKey(order.getId()) ? disputed.get(order.getId()).view() : null),
                        paid.get(order.getPaymentId()),
                        lots.get(order.getAuctionId()),
                        reviewed.get(order.getId())))
                .toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<UUID> paymentIdsOf(List<Order> list) {
        return list.stream().map(Order::getPaymentId).toList();
    }

    private static ResourceNotFoundException notFound(UUID orderId) {
        return new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND, "No order with id " + orderId);
    }
}
