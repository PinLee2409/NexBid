package com.nexbid.order;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.nexbid.auction.AuctionService;
import com.nexbid.order.entity.Order;
import com.nexbid.order.repository.OrderRepository;
import com.nexbid.payment.PaymentEvents;
import com.nexbid.user.AddressBook;

/**
 * EN: Keeps each order in step with its payment (guide §33, spec §17). Plain listeners on purpose: they run
 *     inside the payment's own transaction, so an order can never be PAID while its lot is not COMPLETED.
 * VI: Giữ mỗi đơn hàng đi cùng nhịp với khoản thanh toán của nó (guide §33, spec §17). Cố ý dùng listener
 *     thường: chúng chạy trong chính transaction thanh toán, nên đơn không bao giờ PAID mà lô chưa COMPLETED.
 */
@Component
class OrderBookkeeping {

    private final OrderRepository orders;
    private final AuctionService auctions;
    private final AddressBook addresses;

    OrderBookkeeping(OrderRepository orders, AuctionService auctions, AddressBook addresses) {
        this.orders = orders;
        this.auctions = auctions;
        this.addresses = addresses;
    }

    /** EN: The win opens the order, waiting on payment. / VI: Chiến thắng mở đơn hàng, chờ thanh toán. */
    @EventListener
    void onOpened(PaymentEvents.Opened event) {
        orders.save(new Order(
                event.auctionId(), event.userId(), auctions.sellerOf(event.auctionId()),
                event.paymentId(), event.amount()));
    }

    /**
     * EN: Spec §17: SUCCESS → order PAID → auction COMPLETED (and the product SOLD).
     * VI: Spec §17: SUCCESS → đơn PAID → phiên COMPLETED (và sản phẩm SOLD).
     */
    @EventListener
    void onSucceeded(PaymentEvents.Succeeded event) {
        orders.findByPaymentId(event.paymentId()).ifPresent(order -> {
            order.moveTo(OrderStatus.PAID);
            // EN: A copy, so editing the profile later never redirects a paid parcel.
            // VI: Một bản sao, để sửa hồ sơ sau này không bao giờ đổi hướng kiện hàng đã trả tiền.
            addresses.addressOf(order.getBuyerId()).ifPresent(address ->
                    order.deliverTo(address.recipient(), address.phone(), address.oneLine()));
            orders.save(order);
        });
        auctions.completeSale(event.auctionId());
    }

    @EventListener
    void onExpired(PaymentEvents.Expired event) {
        orders.findByPaymentId(event.paymentId()).ifPresent(order -> {
            order.moveTo(OrderStatus.CANCELLED);
            orders.save(order);
        });
    }
}
