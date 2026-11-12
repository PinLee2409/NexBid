package com.nexbid.payment;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * EN: With the `prod` profile, refuses to start on the repository's placeholder VNPay keys: every checkout would be
 *     rejected by VNPay, and anyone reading the repository could forge a payment confirmation.
 * VI: Với profile `prod`, từ chối khởi động với khoá VNPay giữ chỗ trong repository: mọi checkout sẽ bị VNPay từ chối,
 *     và ai đọc repository cũng làm giả được xác nhận thanh toán.
 */
@Component
@Profile("prod")
class VnpayProductionGuard {

    VnpayProductionGuard(VnpayProperties vnpay) {
        if (vnpay.placeholder()) {
            throw new IllegalStateException("Refusing to start with the 'prod' profile: "
                    + "NEXBID_VNPAY_TMN_CODE and NEXBID_VNPAY_HASH_SECRET are development placeholders");
        }
    }
}
