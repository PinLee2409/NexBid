package com.nexbid.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * EN: An admin's decision: give the money back, or let the sale stand; the note is shown to both sides. A refund is a
 *     bank transfer the admin has already made, so it carries the bank's reference.
 * VI: Quyết định của admin: trả lại tiền, hoặc giữ giao dịch; ghi chú được hiện cho cả hai bên. Hoàn tiền là lần chuyển
 *     khoản admin đã làm, nên kèm mã giao dịch ngân hàng.
 */
public record ResolveDisputeRequest(
        @NotNull Boolean refund,
        @Size(max = 1000) String note,
        @Size(max = 80) String reference) {
}
