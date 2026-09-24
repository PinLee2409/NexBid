package com.nexbid.auction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * EN: What a seller sends to open a lot (guide §14). No sellerId and no status — ownership comes from the
 *     token, and a new auction is always a DRAFT.
 * VI: Những gì người bán gửi để mở một lô (guide §14). Không có sellerId và không có status — chủ sở hữu
 *     lấy từ token, và phiên mới luôn là DRAFT.
 */
public record CreateAuctionRequest(

        @NotNull(message = "Product is required")
        UUID productId,

        // EN: Greater than zero, per spec §7.4. inclusive=false is what makes it "greater", not "at least".
        // VI: Phải lớn hơn 0 theo spec §7.4. inclusive=false mới là "lớn hơn", không phải "từ ... trở lên".
        @NotNull(message = "Starting price is required")
        @DecimalMin(value = "0", inclusive = false, message = "Starting price must be greater than zero")
        BigDecimal startingPrice,

        @NotNull(message = "Minimum increment is required")
        @DecimalMin(value = "0", inclusive = false, message = "Minimum increment must be greater than zero")
        BigDecimal minimumIncrement,

        @NotNull(message = "Start time is required")
        Instant startTime,

        @NotNull(message = "End time is required")
        Instant endTime,

        Boolean antiSnipingEnabled,

        // EN: Bounded so a seller cannot set a window that never closes.
        // VI: Có chặn trên để người bán không đặt được khoảng thời gian không bao giờ kết thúc.
        @Min(value = 5, message = "Anti-sniping window must be at least 5 seconds")
        @Max(value = 600, message = "Anti-sniping window must be at most 10 minutes")
        Integer antiSnipingWindowSeconds,

        @Min(value = 5, message = "Extension must be at least 5 seconds")
        @Max(value = 600, message = "Extension must be at most 10 minutes")
        Integer extensionSeconds) {
}
