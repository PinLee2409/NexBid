package com.nexbid.auction;

import java.util.UUID;

/** EN: A seller or reviewer changed a lot; the audit module records the result in the same transaction.
 * VI: Người bán hoặc người duyệt đổi trạng thái lô; module audit ghi kết quả trong cùng transaction. */
public record AuctionAuditEvent(
        Action action, UUID auctionId, UUID actorId, AuctionStatus before, AuctionStatus after) {

    public enum Action { CREATED, APPROVED, REJECTED }
}
