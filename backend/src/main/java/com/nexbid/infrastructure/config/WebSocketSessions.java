package com.nexbid.infrastructure.config;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * EN: websocket_connections (spec §34): realtime sessions open right now. Kept as a set of session ids, so a
 *     disconnect for a session that never finished connecting cannot push the count below zero.
 * VI: websocket_connections (spec §34): số phiên realtime đang mở. Giữ dạng tập hợp id phiên, để một lần ngắt
 *     kết nối của phiên chưa kịp kết nối xong không thể kéo số đếm xuống dưới 0.
 */
@Component
class WebSocketSessions implements MeterBinder {

    private final Set<String> open = ConcurrentHashMap.newKeySet();

    @EventListener
    void onConnected(SessionConnectedEvent event) {
        String session = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (session != null) {
            open.add(session);
        }
    }

    @EventListener
    void onDisconnect(SessionDisconnectEvent event) {
        open.remove(event.getSessionId());
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("websocket.connections", open, Set::size)
                .description("Realtime sessions open right now")
                .register(registry);
    }
}
