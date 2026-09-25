package com.nexbid.notification;

import java.util.Map;
import java.util.function.Consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexbid.auction.AuctionLifecycleEvent;
import com.nexbid.bid.OutbidEvent;
import com.nexbid.infrastructure.kafka.ConsumedEvents;
import com.nexbid.infrastructure.kafka.EventHeaders;
import com.nexbid.payment.PaymentEvents;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: The Notification Consumer (guide §36, spec §19): reads events from Kafka and turns the ones people
 *     should hear about into notifications. Other event types on the same topics are not its business.
 * VI: Notification Consumer (guide §36, spec §19): đọc sự kiện từ Kafka và biến những sự kiện cần báo cho
 *     người dùng thành thông báo. Các loại sự kiện khác trên cùng topic không phải việc của nó.
 */
@Component
class NotificationConsumer {

    static final String NAME = "notifications";

    private final ConsumedEvents consumed;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final Map<String, Consumer<String>> handlers;

    NotificationConsumer(
            NotificationTriggers triggers, ConsumedEvents consumed, TransactionTemplate tx, ObjectMapper json) {

        this.consumed = consumed;
        this.tx = tx;
        this.json = json;
        this.handlers = Map.of(
                EventHeaders.typeName(OutbidEvent.class),
                body -> triggers.onOutbid(read(body, OutbidEvent.class)),
                EventHeaders.typeName(AuctionLifecycleEvent.class),
                body -> triggers.onLifecycleChange(read(body, AuctionLifecycleEvent.class)),
                EventHeaders.typeName(PaymentEvents.Succeeded.class),
                body -> triggers.onPaymentSucceeded(read(body, PaymentEvents.Succeeded.class)),
                EventHeaders.typeName(PaymentEvents.Expired.class),
                body -> triggers.onPaymentExpired(read(body, PaymentEvents.Expired.class)));
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

        // EN: The "seen it" mark and the notices commit together, so a redelivered event does nothing.
        // VI: Dấu "đã thấy" và các thông báo commit cùng nhau, nên sự kiện bị giao lại không làm gì cả.
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
