package com.nexbid.payment;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.nexbid.user.BuyerStanding;

/**
 * EN: A payment that ran out is a win never paid for; it counts against the buyer, in the expiry's transaction.
 * VI: Khoản thanh toán hết hạn là một lần thắng không trả tiền; tính vào người mua, trong transaction của lần hết hạn.
 */
@Component
class UnpaidWins {

    private final BuyerStanding standing;

    UnpaidWins(BuyerStanding standing) {
        this.standing = standing;
    }

    @EventListener
    void onExpired(PaymentEvents.Expired event) {
        standing.recordUnpaidWin(event.userId());
    }
}
