package com.nexbid.message;

/**
 * EN: Where a signed-in user's order messages arrive over the socket.
 * VI: Nơi tin nhắn đơn hàng của người đang đăng nhập tới qua socket.
 */
public final class MessageChannel {

    public static final String QUEUE = "/queue/messages";

    public static final String SUBSCRIPTION = "/user" + QUEUE;

    private MessageChannel() {
    }
}
