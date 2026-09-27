package com.nexbid.analytics;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexbid.auction.AuctionLifecycleEvent;
import com.nexbid.bid.BidPlacedEvent;
import com.nexbid.infrastructure.kafka.ConsumedEvents;
import com.nexbid.infrastructure.kafka.EventHeaders;
import com.nexbid.payment.PaymentEvents;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: The Analytics Consumer (spec §19): its own consumer group on the same topics as the notifications,
 *     so it reads every event independently and can fall behind or catch up without affecting anyone.
 *     Each event is counted into the hour it happened in, once.
 * VI: Analytics Consumer (spec §19): consumer group riêng trên cùng các topic với thông báo, nên nó đọc mọi sự
 *     kiện một cách độc lập và có chậm hay đuổi kịp cũng không ảnh hưởng ai. Mỗi sự kiện được cộng vào đúng giờ
 *     nó xảy ra, một lần.
 */
@Component
class AnalyticsConsumer {

    static final String NAME = "analytics";

    private final ConsumedEvents consumed;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final Map<String, Consumer<String>> handlers;

    AnalyticsConsumer(AnalyticsService analytics, ConsumedEvents consumed, TransactionTemplate tx, ObjectMapper json) {
        this.consumed = consumed;
        this.tx = tx;
        this.json = json;
        this.handlers = Map.of(
                EventHeaders.typeName(BidPlacedEvent.class),
                body -> analytics.add(read(body, BidPlacedEvent.class).placed().bid().createdAt(),
                        1, 0, 0, 0, BigDecimal.ZERO),
                EventHeaders.typeName(AuctionLifecycleEvent.class),
                body -> {
                    AuctionLifecycleEvent event = read(body, AuctionLifecycleEvent.class);
                    if (AuctionLifecycleEvent.ENDED.equals(event.type())) {
                        analytics.add(event.endTime(), 0, 1, event.winnerId() == null ? 0 : 1, 0, BigDecimal.ZERO);
                    }
                },
                EventHeaders.typeName(PaymentEvents.Succeeded.class),
                body -> {
                    PaymentEvents.Succeeded paid = read(body, PaymentEvents.Succeeded.class);
                    analytics.add(paid.at(), 0, 0, 0, 1, paid.amount());
                });
    }

    @KafkaListener(
            id = NAME,
            groupId = "nexbid-" + NAME,
            topics = {"nexbid.auctions", "nexbid.payments"},
            concurrency = "${nexbid.kafka.consumer-concurrency}")
    void on(ConsumerRecord<String, String> record) {
        Consumer<String> handler = handlers.get(EventHeaders.typeOf(record));
        if (handler == null) {
            return;
        }

        // EN: The "counted it" mark and the totals commit together, so a redelivery adds nothing.
        // VI: Dấu "đã đếm" và số liệu tổng commit cùng nhau, nên lần giao lại không cộng thêm gì.
        tx.executeWithoutResult(status -> {
            if (consumed.firstTime(NAME, EventHeaders.idOf(record))) {
                handler.accept(record.value());
            }
        });
    }

    private <T> T read(String body, Class<T> type) {
        return json.readValue(body, type);
    }
}
