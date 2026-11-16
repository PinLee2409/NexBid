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
        Integer extensionSeconds,

        // EN: Optional. Kept from bidders; how it relates to the other prices is checked by the service.
        // VI: Không bắt buộc. Giấu người trả giá; quan hệ với các giá khác do service kiểm.
        @DecimalMin(value = "0", inclusive = false, message = "Reserve price must be greater than zero")
        BigDecimal reservePrice,

        // EN: Optional. Offered until the first bid. / VI: Không bắt buộc. Có hiệu lực tới lượt trả giá đầu tiên.
        @DecimalMin(value = "0", inclusive = false, message = "Buy-now price must be greater than zero")
        BigDecimal buyNowPrice,

        // EN: Optional; the seller pays the parcel. / VI: Không bắt buộc; người bán trả tiền gửi kiện.
        Boolean freeShipping,

        // EN: Optional: relist automatically up to this many times when it closes unsold. / VI: Không bắt buộc: tự đăng lại tối đa bấy nhiêu lần khi đóng mà không bán được.
        @Min(value = 0, message = "Relisting must be between 0 and 5 times")
        @Max(value = 5, message = "Relisting must be between 0 and 5 times")
        Integer autoRelist,

        // EN: Optional: how much lower each relist starts, in percent. / VI: Không bắt buộc: mỗi lần đăng lại khởi điểm thấp hơn bao nhiêu phần trăm.
        @Min(value = 0, message = "The price drop must be between 0 and 50%")
        @Max(value = 50, message = "The price drop must be between 0 and 50%")
        Integer relistDropPercent) {

    public CreateAuctionRequest(UUID productId, BigDecimal startingPrice, BigDecimal minimumIncrement,
            Instant startTime, Instant endTime, Boolean antiSnipingEnabled, Integer antiSnipingWindowSeconds,
            Integer extensionSeconds, BigDecimal reservePrice, BigDecimal buyNowPrice, Boolean freeShipping) {
        this(productId, startingPrice, minimumIncrement, startTime, endTime, antiSnipingEnabled,
                antiSnipingWindowSeconds, extensionSeconds, reservePrice, buyNowPrice, freeShipping, null, null);
    }


    public CreateAuctionRequest(UUID productId, BigDecimal startingPrice, BigDecimal minimumIncrement,
            Instant startTime, Instant endTime, Boolean antiSnipingEnabled, Integer antiSnipingWindowSeconds,
            Integer extensionSeconds, BigDecimal reservePrice, BigDecimal buyNowPrice) {
        this(productId, startingPrice, minimumIncrement, startTime, endTime, antiSnipingEnabled,
                antiSnipingWindowSeconds, extensionSeconds, reservePrice, buyNowPrice, null);
    }
}
