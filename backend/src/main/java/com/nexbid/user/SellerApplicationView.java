package com.nexbid.user;

import java.time.Instant;
import java.util.UUID;

/**
 * EN: A request to become a seller, with who sent it — the admin queue shows both.
 * VI: Một yêu cầu trở thành người bán, kèm người gửi — hàng chờ của admin cần cả hai.
 */
public record SellerApplicationView(
        UUID id,
        UUID userId,
        String fullName,
        String email,
        String note,
        SellerApplicationStatus status,
        String rejectionReason,
        Instant createdAt,
        Instant decidedAt) {
}
