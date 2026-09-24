package com.nexbid.auction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * EN: Why an auction was refused. Required — a seller who is told no deserves to know what to fix.
 * VI: Lý do phiên bị từ chối. Bắt buộc — người bán bị từ chối xứng đáng biết phải sửa gì.
 */
public record RejectAuctionRequest(

        @NotBlank(message = "A reason is required")
        @Size(max = 500, message = "The reason must be at most 500 characters")
        String reason) {
}
