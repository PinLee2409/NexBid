package com.nexbid.infrastructure.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;

/**
 * EN: Keeps traces to the work people do (spec §34): requests and the events they cause. Without these rules,
 *     Prometheus scrapes and the outbox relay's polling would each start a trace every few seconds.
 * VI: Giữ trace cho những việc người dùng làm (spec §34): request và các sự kiện chúng gây ra. Không có các luật này,
 *     lượt Prometheus thu metric và lượt quét của outbox relay sẽ mở một trace mới mỗi vài giây.
 */
@Configuration(proxyBeanMethods = false)
public class ObservabilityConfig {

    /** EN: Health checks and metric scrapes are not traffic. / VI: Health check và thu metric không phải lưu lượng thật. */
    @Bean
    ObservationPredicate noActuatorTraces() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext request)
                || !request.getCarrier().getRequestURI().startsWith("/actuator");
    }

    /**
     * EN: SQL only as part of something already traced; a query on its own would be a trace of one span. A scheduler
     *     tick is "in" an observation too, a switched-off one, which does not count.
     * VI: SQL chỉ được trace khi thuộc về một việc đã được trace; một câu truy vấn đứng riêng sẽ thành trace chỉ một span.
     *     Nhịp scheduler cũng "nằm trong" một observation, loại đã bị tắt, và loại đó không tính.
     */
    @Bean
    ObservationPredicate sqlOnlyInsideATrace(ObjectProvider<ObservationRegistry> registry) {
        return (name, context) -> {
            if (!name.startsWith("jdbc.")) {
                return true;
            }
            Observation current = registry.getObject().getCurrentObservation();
            return current != null && !current.isNoop();
        };
    }
}
