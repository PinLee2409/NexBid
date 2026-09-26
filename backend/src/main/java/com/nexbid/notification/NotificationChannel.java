package com.nexbid.notification;

/**
 * EN: Each signed-in user's private realtime inbox (spec §18). The server sends to {@link #QUEUE} for one user
 *     id; Spring delivers it only to sockets that signed in as that user and subscribed to {@link #SUBSCRIPTION}.
 * VI: Hộp thư realtime riêng của từng người đã đăng nhập (spec §18). Server gửi tới {@link #QUEUE} cho một user
 *     id; Spring chỉ giao cho các socket đăng nhập đúng user đó và đã đăng ký {@link #SUBSCRIPTION}.
 */
public final class NotificationChannel {

    public static final String QUEUE = "/queue/notifications";

    public static final String SUBSCRIPTION = "/user" + QUEUE;

    private NotificationChannel() {
    }
}
