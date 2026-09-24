package com.nexbid.payment;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.nexbid.auction.AuctionLifecycleEvent;

/**
 * EN: Opens a payment the moment a lot closes with a winner (spec §12: set winner → ENDED → create
 *     payment). A plain listener on purpose: it runs inside the closing transaction, so if the payment
 *     cannot be written the close is undone and retried on the next tick.
 * VI: Mở khoản thanh toán ngay khi lô đóng có người thắng (spec §12: chọn người thắng → ENDED → tạo thanh
 *     toán). Cố ý dùng listener thường: nó chạy trong transaction đóng phiên, nên ghi thanh toán hỏng thì
 *     việc đóng bị hoàn tác và thử lại ở nhịp sau.
 */
@Component
class PaymentOpening {

    private final PaymentService payments;

    PaymentOpening(PaymentService payments) {
        this.payments = payments;
    }

    @EventListener
    void onLifecycleChange(AuctionLifecycleEvent event) {
        if (AuctionLifecycleEvent.ENDED.equals(event.type()) && event.winnerId() != null) {
            payments.openFor(event.auctionId(), event.winnerId(), event.currentPrice(), event.endTime());
        }
    }
}
