package com.nexbid.bid.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * EN: The ceiling for an auto bid (spec §14). Whether it clears the next minimum is checked against the
 *     lot under its lock, not here.
 * VI: Mức trần của auto bid (spec §14). Việc nó có vượt mức tối thiểu kế tiếp hay không được kiểm trên lô
 *     trong khoá của nó, không phải ở đây.
 */
public record AutoBidRequest(

        @NotNull(message = "Max amount is required")
        @DecimalMin(value = "0", inclusive = false, message = "Max amount must be greater than zero")
        @Digits(integer = 13, fraction = 2, message = "Max amount is too large")
        BigDecimal maxAmount) {
}
