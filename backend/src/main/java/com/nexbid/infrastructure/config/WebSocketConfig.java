package com.nexbid.infrastructure.config;

import java.security.Principal;
import java.util.UUID;

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
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.nexbid.auction.AuctionChannel;
import com.nexbid.auth.TokenAuthenticator;
import com.nexbid.notification.NotificationChannel;

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
    private final TokenAuthenticator tokens;

    public WebSocketConfig(@Value("${nexbid.web.allowed-origins}") String[] allowedOrigins, TokenAuthenticator tokens) {
        this.allowedOrigins = allowedOrigins;
        this.tokens = tokens;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // EN: A browser WebSocket ignores CORS but the handshake does not, so the origins are named here.
        //     This constrains browsers only — it is not authentication; that happens on CONNECT, below.
        // VI: WebSocket của trình duyệt bỏ qua CORS nhưng bước bắt tay thì không, nên phải khai origin ở
        //     đây. Nó chỉ ràng buộc trình duyệt — không phải xác thực; việc đó làm ở CONNECT, bên dưới.
        registry.addEndpoint("/ws").setAllowedOrigins(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // EN: /topic for lots everyone may watch, /queue for each user's own inbox.
        // VI: /topic cho các lô ai cũng xem được, /queue cho hộp thư riêng của từng người.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");

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
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }
                StompCommand command = accessor.getCommand();

                // EN: A signed-in browser sends its token with CONNECT. Without a usable one the socket stays
                //     anonymous: it can still watch lots, it just has no inbox.
                // VI: Trình duyệt đã đăng nhập gửi token kèm CONNECT. Không có token dùng được thì socket vẫn ẩn
                //     danh: vẫn xem được các lô, chỉ là không có hộp thư.
                if (command == StompCommand.CONNECT) {
                    tokens.authenticate(accessor.getFirstNativeHeader("Authorization"))
                            .ifPresent(user -> accessor.setUser(new SocketUser(user.id())));
                }

                if (command == StompCommand.SEND) {
                    throw new MessagingException("This channel does not accept messages from clients");
                }

                if (command == StompCommand.SUBSCRIBE) {
                    String destination = accessor.getDestination();
                    boolean lot = destination != null && destination.startsWith(AuctionChannel.TOPIC_PREFIX);
                    boolean inbox = NotificationChannel.SUBSCRIPTION.equals(destination) && accessor.getUser() != null;

                    // EN: Two shapes of destination exist. Anything else is a client exploring.
                    // VI: Chỉ có hai dạng đích tồn tại. Ngoài ra là client đang dò tìm.
                    if (!lot && !inbox) {
                        throw new MessagingException("No such destination");
                    }
                }

                return message;
            }
        });
    }

    /**
     * EN: Named by user id, so the server can address a user's inbox by the id it already has.
     * VI: Đặt tên theo user id, để server gửi tới hộp thư của một người bằng chính id nó đang có.
     */
    private record SocketUser(UUID id) implements Principal {

        @Override
        public String getName() {
            return id.toString();
        }
    }
}
