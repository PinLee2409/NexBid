package com.nexbid.user;

import java.time.Instant;
import java.util.UUID;

/**
 * EN: An admin gave or took away a seller's verified badge.
 * VI: Admin đã cấp hoặc gỡ huy hiệu xác minh của người bán.
 */
public record SellerVerifiedEvent(UUID sellerId, UUID actorId, boolean verified, Instant at) {
}
