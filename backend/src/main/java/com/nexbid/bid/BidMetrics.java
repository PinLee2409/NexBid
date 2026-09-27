package com.nexbid.bid;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.nexbid.common.exception.BusinessException;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * EN: The bid metrics of spec §34: bid_requests_total, bid_success_total, bid_failed_total (by reason) and
 *     bid_latency. Measured at the door, so a request refused by the rate limiter counts too.
 * VI: Các metric trả giá của spec §34: bid_requests_total, bid_success_total, bid_failed_total (theo lý do) và
 *     bid_latency. Đo ngay tại cửa, nên request bị bộ giới hạn tần suất từ chối cũng được tính.
 */
@Component
class BidMetrics {

    private final MeterRegistry registry;
    private final Counter requests;
    private final Counter successes;
    private final Timer latency;

    BidMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.requests = Counter.builder("bid.requests").description("Bid requests received").register(registry);
        this.successes = Counter.builder("bid.success").description("Bids accepted").register(registry);
        // EN: A histogram, so a dashboard can work out p95 across instances. / VI: Dạng histogram, để dashboard tính được p95 trên nhiều instance.
        this.latency = Timer.builder("bid.latency")
                .description("Time to answer a bid request")
                .publishPercentileHistogram()
                .register(registry);
    }

    <T> T measure(Supplier<T> bid) {
        requests.increment();
        Timer.Sample sample = Timer.start(registry);
        try {
            T placed = bid.get();
            successes.increment();
            return placed;
        } catch (BusinessException ex) {
            failed(ex.code().name());
            throw ex;
        } catch (RuntimeException ex) {
            failed("INTERNAL_ERROR");
            throw ex;
        } finally {
            sample.stop(latency);
        }
    }

    private void failed(String reason) {
        Counter.builder("bid.failed").description("Bids refused, by error code").tag("reason", reason)
                .register(registry).increment();
    }
}
