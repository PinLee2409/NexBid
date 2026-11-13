package com.nexbid.user;

import java.time.Instant;
import java.util.UUID;

/**
 * EN: Someone set or changed where NexBid sends them money. {@code before} / {@code after} are masked
 *     ("VCB ••••1234"); {@code before} is null the first time.
 * VI: Một người đặt hoặc đổi nơi NexBid chuyển tiền cho họ. {@code before} / {@code after} đã che bớt
 *     ("VCB ••••1234"); {@code before} là null ở lần đầu.
 */
public record BankAccountSavedEvent(UUID userId, String before, String after, Instant at) {
}
