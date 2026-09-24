package com.nexbid.bid.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * EN: What a bidder sends (guide §20). Just the amount — who is bidding and on what come from the token
 *     and the URL, so neither can be forged in the body.
 * VI: Những gì người trả giá gửi lên (guide §20). Chỉ số tiền — ai trả và trả cho lô nào lấy từ token và
 *     URL, nên không thể giả mạo trong body.
 */
public record PlaceBidRequest(

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0", inclusive = false, message = "Amount must be greater than zero")
        BigDecimal amount) {
}
