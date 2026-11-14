package com.nexbid.notification;

import java.time.Instant;

import org.springframework.boot.test.context.TestComponent;

/**
 * EN: Lets a test in another package run the completion reminder, which is package-private like the other triggers.
 * VI: Cho test ở package khác chạy lời nhắc hoàn tất, vốn package-private giống các trigger khác.
 */
@TestComponent
public class CompletionReminders {

    private final NotificationTriggers triggers;

    CompletionReminders(NotificationTriggers triggers) {
        this.triggers = triggers;
    }

    public int send(Instant now) {
        return triggers.sendCompletionReminders(now);
    }
}
