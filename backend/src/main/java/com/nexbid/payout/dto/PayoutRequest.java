package com.nexbid.payout.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * EN: The admin made the transfer; {@code reference} is the bank's transaction code, to trace it later.
 * VI: Admin đã chuyển khoản; {@code reference} là mã giao dịch của ngân hàng, để tra lại sau này.
 */
public record PayoutRequest(@NotNull UUID sellerId, @NotBlank @Size(max = 80) String reference) {
}
