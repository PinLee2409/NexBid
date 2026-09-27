package com.nexbid.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * EN: A payment the winner still owes, with its deadline — what a reminder needs to say.
 * VI: Một khoản người thắng vẫn còn nợ, kèm hạn chót — những gì lời nhắc cần nói.
 */
public record PaymentDue(UUID paymentId, UUID auctionId, UUID userId, BigDecimal amount, Instant deadline) {
}
