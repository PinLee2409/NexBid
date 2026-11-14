package com.nexbid.question;

import java.time.Instant;
import java.util.UUID;

/**
 * EN: What happened to a question; the notification module tells the other side.
 * VI: Chuyện gì đã xảy ra với một câu hỏi; module notification báo cho bên còn lại.
 */
public final class QuestionEvents {

    private QuestionEvents() {
    }

    /** EN: A buyer asked; the seller hears of it. / VI: Người mua đã hỏi; người bán được báo. */
    public record Asked(UUID questionId, UUID auctionId, UUID sellerId, UUID askerId, String body, Instant at) {
    }

    /** EN: The seller answered; the asker hears of it. / VI: Người bán đã trả lời; người hỏi được báo. */
    public record Answered(UUID questionId, UUID auctionId, UUID askerId, String body, String answer, Instant at) {
    }
}
