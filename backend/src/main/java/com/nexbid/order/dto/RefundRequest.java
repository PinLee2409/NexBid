package com.nexbid.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * EN: The bank's reference of the transfer the admin made back to the buyer.
 * VI: Mã giao dịch ngân hàng của lần chuyển khoản admin đã trả lại người mua.
 */
public record RefundRequest(@NotBlank @Size(max = 80) String reference) {
}
