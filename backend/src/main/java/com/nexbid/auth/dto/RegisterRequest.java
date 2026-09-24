package com.nexbid.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * EN: Registration input (guide §6). Every rule here is checked before any service code runs.
 * VI: Dữ liệu đăng ký (guide §6). Mọi ràng buộc ở đây được kiểm trước khi service chạy.
 */
public record RegisterRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be at most 120 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is not valid")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        // EN: Minimum 8 per the guide. No maximum below BCrypt's 72-byte limit is imposed here.
        // VI: Tối thiểu 8 ký tự theo guide. Không đặt trần dưới giới hạn 72 byte của BCrypt.
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password) {
}
