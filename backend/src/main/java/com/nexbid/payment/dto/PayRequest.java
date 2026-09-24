package com.nexbid.payment.dto;

import com.nexbid.payment.PaymentOutcome;

import jakarta.validation.constraints.NotNull;

/**
 * EN: The demo switch: SUCCESS or FAILED (spec §17).
 * VI: Công tắc demo: SUCCESS hoặc FAILED (spec §17).
 */
public record PayRequest(@NotNull(message = "Outcome is required") PaymentOutcome outcome) {
}
