package com.nexbid.question;

import java.time.Instant;
import java.util.UUID;

/**
 * EN: One question as everyone sees it: the asker masked like a bidder, the answer when there is one.
 * VI: Một câu hỏi như mọi người thấy: tên người hỏi được che như người trả giá, câu trả lời nếu đã có.
 */
public record QuestionView(UUID id, UUID auctionId, String asker, String body, String answer, Instant createdAt,
        Instant answeredAt) {
}
