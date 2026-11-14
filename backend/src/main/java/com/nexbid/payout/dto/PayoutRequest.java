package com.nexbid.payout.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * EN: The admin made the transfer; {@code reference} is the bank's transaction code, to trace it later. {@code amount}
 *     is the balance the admin saw and transferred: if more sales completed since, the payout is refused rather than
 *     recorded for money that was never sent.
 * VI: Admin đã chuyển khoản; {@code reference} là mã giao dịch của ngân hàng, để tra lại sau này. {@code amount} là số dư
 *     admin đã thấy và đã chuyển: nếu có thêm đơn hoàn tất kể từ lúc đó, lần chi trả bị từ chối thay vì ghi nhận số tiền
 *     chưa hề được chuyển.
 */
public record PayoutRequest(@NotNull UUID sellerId, @NotBlank @Size(max = 80) String reference,
        @NotNull BigDecimal amount) {
}
