package com.nexbid.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * EN: A buyer's complaint: why, and what happened in their own words.
 * VI: Khiếu nại của người mua: vì sao, và chuyện gì đã xảy ra theo lời họ.
 */
public record DisputeRequest(
        @NotBlank @Pattern(regexp = "NOT_RECEIVED|NOT_AS_DESCRIBED|DAMAGED|OTHER") String reason,
        @NotBlank @Size(min = 10, max = 1000) String details) {
}
