package com.nexbid.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.nexbid.auction.AuctionChannel;

/**
 * EN: The realtime channel (guide §23). STOMP over a plain WebSocket, with an in-memory broker — one
 *     server, one process, no external broker to run.
 * VI: Kênh realtime (guide §23). STOMP chạy trên WebSocket thuần, broker nằm trong bộ nhớ — một server,
 *     một tiến trình, không phải dựng thêm broker bên ngoài.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final String[] allowedOrigins;

    public WebSocketConfig(@Value("${nexbid.web.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // EN: A browser WebSocket ignores CORS but the handshake does not, so the origins are named here.
        //     This constrains browsers only — it is not authentication, and nothing secret goes over this.
        // VI: WebSocket của trình duyệt bỏ qua CORS nhưng bước bắt tay thì không, nên phải khai origin ở
        //     đây. Nó chỉ ràng buộc trình duyệt — không phải xác thực, và không có gì bí mật đi qua đây.
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");

        // EN: No application destination prefix (spec §46): there is nothing for a client to call here.
        // VI: Không khai tiền tố đích cho ứng dụng (spec §46): ở đây không có gì để client gọi.
    }

    /**
     * EN: Clients may listen and nothing else. Leaving this out is not the same as having no inbound
     *     route: the broker relays a client's SEND straight to every subscriber, so anyone could have
     *     broadcast an invented price to every browser watching a lot.
     * VI: Client chỉ được nghe, không được gì khác. Bỏ qua chỗ này không giống với "không có đường vào":
     *     broker chuyển thẳng lệnh SEND của client tới mọi người đăng ký, nên bất kỳ ai cũng có thể phát
     *     một mức giá bịa ra tới mọi trình duyệt đang xem một lô.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {

            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompCommand command = StompHeaderAccessor.wrap(message).getCommand();

                if (command == StompCommand.SEND) {
                    throw new MessagingException("This channel does not accept messages from clients");
                }

                if (command == StompCommand.SUBSCRIBE) {
                    String destination = StompHeaderAccessor.wrap(message).getDestination();

                    // EN: One shape of destination exists. Anything else is a client exploring.
                    // VI: Chỉ có một dạng đích tồn tại. Ngoài ra là client đang dò tìm.
                    if (destination == null || !destination.startsWith(AuctionChannel.TOPIC_PREFIX)) {
                        throw new MessagingException("No such destination");
                    }
                }

                return message;
            }
        });
    }
}
