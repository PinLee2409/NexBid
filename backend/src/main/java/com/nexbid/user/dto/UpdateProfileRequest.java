package com.nexbid.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * EN: The only thing a user may change about themselves (guide §9). Role, status and id are not blocked —
 *     they simply have no field here, so there is nothing to send.
 * VI: Thứ duy nhất người dùng được sửa của chính mình (guide §9). Role, status và id không phải bị chặn —
 *     chúng đơn giản là không có field ở đây, nên không có gì để gửi lên.
 */
public record UpdateProfileRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be at most 120 characters")
        String fullName) {
}
