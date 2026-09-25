package com.nexbid.infrastructure.kafka;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.util.ClassUtils;

/**
 * EN: What travels with every event on Kafka besides its JSON body: a unique id and a type name.
 * VI: Những gì đi kèm mỗi sự kiện trên Kafka ngoài phần thân JSON: một id duy nhất và tên loại sự kiện.
 */
public final class EventHeaders {

    // EN: Same for every delivery of one event, so a consumer can tell a repeat. / VI: Giống nhau ở mọi lần giao của một sự kiện, để consumer nhận ra lần lặp.
    public static final String ID = "nexbid-event-id";
    public static final String TYPE = "nexbid-event-type";

    private EventHeaders() {
    }

    /**
     * EN: The type name on the wire: the class name without its package, e.g. "PaymentEvents.Succeeded".
     * VI: Tên loại trên đường truyền: tên class bỏ package, ví dụ "PaymentEvents.Succeeded".
     */
    public static String typeName(Class<?> eventType) {
        return ClassUtils.getShortName(eventType);
    }

    public static String typeOf(ConsumerRecord<?, ?> record) {
        return valueOf(record, TYPE);
    }

    public static UUID idOf(ConsumerRecord<?, ?> record) {
        String id = valueOf(record, ID);
        return id == null ? null : UUID.fromString(id);
    }

    private static String valueOf(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
