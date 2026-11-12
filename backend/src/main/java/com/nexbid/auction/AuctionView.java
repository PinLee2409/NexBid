package com.nexbid.auction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * EN: An auction as the API returns it.
 * VI: Một phiên đấu giá dưới dạng API trả về.
 */
public record AuctionView(
        UUID id,
        // EN: Catalogue number, shown as "Lot 001". / VI: Số thứ tự trong danh mục, hiển thị như "Lot 001".
        Long lotNumber,
        UUID productId,
        UUID sellerId,
        BigDecimal startingPrice,
        BigDecimal currentPrice,
        BigDecimal minimumIncrement,
        Instant startTime,
        Instant endTime,
        AuctionStatus status,
        AntiSniping antiSniping,
        int extensionCount,
        int bidCount,
        UUID winnerId,
        String rejectionReason,
        // EN: The amount only for the seller and admins; everyone else learns only whether there is one, and whether
        //     it is met. / VI: Con số chỉ dành cho người bán và admin; người khác chỉ biết có giá sàn không, và đã đạt chưa.
        BigDecimal reservePrice,
        boolean hasReserve,
        Boolean reserveMet,
        BigDecimal buyNowPrice,
        boolean boughtNow,
        ReserveDecision reserveDecision,
        Instant reserveDecisionUntil,
        Instant createdAt,
        Instant updatedAt) {

    /** EN: Grouped, because these three only mean anything together. / VI: Gom lại, vì ba giá trị này chỉ có nghĩa khi đi cùng nhau. */
    public record AntiSniping(boolean enabled, int windowSeconds, int extensionSeconds) {
    }
}
