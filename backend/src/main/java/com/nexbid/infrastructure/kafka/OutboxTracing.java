package com.nexbid.infrastructure.kafka;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;

/**
 * EN: Carries a trace across the outbox (spec §34). The writer keeps the traceparent of the request that raised an
 *     event; the relay sends the event inside a span that continues it, and Kafka's own header takes it from there.
 * VI: Mang trace đi qua outbox (spec §34). Writer giữ traceparent của request đã phát ra sự kiện; relay gửi sự kiện
 *     trong một span nối tiếp trace đó, và header của chính Kafka mang nó đi tiếp.
 */
@Component
class OutboxTracing {

    static final String TRACE_PARENT = "traceparent";

    private final ObservationRegistry observations;
    private final Tracer tracer;
    private final Propagator propagator;

    OutboxTracing(
            ObjectProvider<ObservationRegistry> observations,
            ObjectProvider<Tracer> tracer,
            ObjectProvider<Propagator> propagator) {
        this.observations = observations.getIfAvailable(() -> ObservationRegistry.NOOP);
        this.tracer = tracer.getIfAvailable(() -> Tracer.NOOP);
        this.propagator = propagator.getIfAvailable(() -> Propagator.NOOP);
    }

    /** EN: The span in progress as a W3C traceparent, or null outside one. / VI: Span đang chạy dưới dạng W3C traceparent, hoặc null nếu không có. */
    String current() {
        Span span = tracer.currentSpan();
        if (span == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(span.context(), carrier, Map::put);
        return carrier.get(TRACE_PARENT);
    }

    /**
     * EN: Runs a send as the "outbox relay" span of the stored trace — or of a new one when none was stored. The gap
     *     before it is the time the event waited in the outbox.
     * VI: Chạy việc gửi dưới dạng span "outbox relay" của trace đã lưu — hoặc của trace mới nếu không lưu gì. Khoảng
     *     trống trước nó là thời gian sự kiện nằm chờ trong outbox.
     */
    <T> T relay(String topic, String type, String traceParent, Supplier<T> send) {
        ReceiverContext<Map<String, String>> context = new ReceiverContext<>(Map::get);
        context.setCarrier(traceParent == null ? Map.of() : Map.of(TRACE_PARENT, traceParent));
        context.setRemoteServiceName("outbox");
        return Observation.createNotStarted("nexbid.outbox.relay", () -> context, observations)
                .contextualName("outbox relay " + topic)
                .lowCardinalityKeyValue("topic", topic)
                .highCardinalityKeyValue("event.type", type)
                .observe(send);
    }
}
