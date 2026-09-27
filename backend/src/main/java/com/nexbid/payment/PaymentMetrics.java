package com.nexbid.payment;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * EN: payment_success_total (spec §34). Counted after commit, so a payment a rollback took back is not counted.
 * VI: payment_success_total (spec §34). Đếm sau khi commit, để khoản bị rollback lấy lại không bị tính.
 */
@Component
class PaymentMetrics {

    private final Counter successes;

    PaymentMetrics(MeterRegistry registry) {
        this.successes = Counter.builder("payment.success").description("Payments that went through").register(registry);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSucceeded(PaymentEvents.Succeeded event) {
        successes.increment();
    }
}
