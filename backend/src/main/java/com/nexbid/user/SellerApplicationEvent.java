package com.nexbid.user;

import java.time.Instant;
import java.util.UUID;

/**
 * EN: A request to become a seller was sent (PENDING, actor = applicant) or decided (actor = admin).
 * VI: Một yêu cầu trở thành người bán vừa được gửi (PENDING, người làm = người nộp) hoặc được quyết định (người làm = admin).
 */
public record SellerApplicationEvent(
        UUID applicationId, UUID userId, UUID actorId, SellerApplicationStatus status, String reason, Instant at) {
}
